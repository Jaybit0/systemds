package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RewriterRuleSet {

	public static RewriterRuleSet selectionPushdown;

	static {
		//String rule = "A::MATRIX;B::MATRIX;i::INT;j::INT;res1=add(A,B);out=rowSelect(res1,i,j) => ";
		RuleContext ctx = RuleContext.selectionPushdownContext;

		RewriterRule ruleSelectionPushdown = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction("RowSelectPushableBinaryInstruction") // This is more a class of instructions
					.addOp("A")
						.ofType("MATRIX")
					.addOp("B")
						.ofType("MATRIX")
					.as("A + B")
				.withInstruction("rowSelect")
					.addExistingOp("A + B")
					.addOp("i")
						.ofType("INT")
					.addOp("j")
						.ofType("INT")
					.asRootInstruction()
				.toInstruction("rowSelect")
					.addExistingOp("A")
					.addExistingOp("i")
					.addExistingOp("j")
					.as("rowSelect(A,i,j)")
				.toInstruction("rowSelect")
					.addExistingOp("B")
					.addExistingOp("i")
					.addExistingOp("j")
					.as("rowSelect(B,i,j)")
				.toInstruction("RowSelectPushableBinaryInstruction")
					.addExistingOp("rowSelect(A,i,j)")
					.addExistingOp("rowSelect(B,i,j)")
					.asRootInstruction()
				.build();

		RewriterRule ruleEliminateMultipleSelects = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction("rowSelect")
					.addOp("A")
						.ofType("MATRIX")
					.addOp("i")
						.ofType("INT")
					.addOp("j")
						.ofType("INT")
					.as("tmp1")
				.withInstruction("rowSelect")
					.addExistingOp("tmp1")
					.addOp("k")
						.ofType("INT")
					.addOp("l")
						.ofType("INT")
					.asRootInstruction()
				.toInstruction("max")
					.addExistingOp("i")
					.addExistingOp("k")
					.as("max(i,k)")
				.toInstruction("min")
					.addExistingOp("j")
					.addExistingOp("l")
					.as("min(j,l)")
				.toInstruction("rowSelect")
					.addExistingOp("A")
					.addExistingOp("max(i,k)")
					.addExistingOp("min(j,l)")
					.asRootInstruction()
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleSelectionPushdown);
		rules.add(ruleEliminateMultipleSelects);

		selectionPushdown = new RewriterRuleSet(ctx, rules);
	}

	class ApplicableRule {
		public final ArrayList<RewriterStatement.MatchingSubexpression> matches;
		public final RewriterRule rule;
		public final boolean forward;

		public ApplicableRule(ArrayList<RewriterStatement.MatchingSubexpression> matches, RewriterRule rule, boolean forward) {
			this.matches = matches;
			this.rule = rule;
			this.forward = forward;
		}

		public String toString(final RuleContext ctx) {
			StringBuilder builder = new StringBuilder();
			builder.append("Rule: " + rule + "\n\n");
			int ctr = 1;
			for (RewriterStatement.MatchingSubexpression match : matches) {
				builder.append("Match " + ctr++ + ": \n");
				builder.append(" " + match.getMatchRoot() + " = " + (forward ? rule.getStmt1() : rule.getStmt2())  + "\n\n");
				for (Map.Entry<RewriterStatement, RewriterStatement> entry : match.getAssocs().entrySet()) {
					builder.append(" - " + entry.getKey() + "::" + (ctx == null ? "?" : entry.getKey().getResultingDataType(ctx)) + " -> " + entry.getValue().getId() + "::" + (ctx == null ? "?" : entry.getValue().getResultingDataType(ctx)) + "\n");
				}
				builder.append("\n");
			}

			return builder.toString();
		}

		@Override
		public String toString() {
			return toString(null);
		}
	}

	private RuleContext ctx;
	private List<RewriterRule> rules;

	public RewriterRuleSet(RuleContext ctx, List<RewriterRule> rules) {
		this.ctx = ctx;
		this.rules = rules;
	}

	public RuleContext getContext() {
		return ctx;
	}

	public ApplicableRule findFirstApplicableRule(RewriterInstruction instr) {
		ArrayList<RewriterStatement.MatchingSubexpression> matches = new ArrayList<>();

		for (RewriterRule rule : rules) {
			if (rule.getStmt1().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, true)) {
				return new ApplicableRule(matches, rule, true);
			}

			if (!rule.isUnidirectional()) {
				if (rule.getStmt2().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, true)) {
					return new ApplicableRule(matches, rule, false);
				}
			}
		}

		return null;
	}

	public ArrayList<ApplicableRule> findApplicableRules(RewriterInstruction instr) {
		ArrayList<ApplicableRule> applicableRules = new ArrayList<>();
		ArrayList<RewriterStatement.MatchingSubexpression> matches = new ArrayList<>();

		for (RewriterRule rule : rules) {
			if (rule.getStmt1().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, false)) {
				applicableRules.add(new ApplicableRule(matches, rule, true));
				matches = new ArrayList<>();
			}

			if (!rule.isUnidirectional()) {
				if (rule.getStmt2().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, false)) {
					applicableRules.add(new ApplicableRule(matches, rule, false));
					matches = new ArrayList<>();
				}
			}
		}

		return applicableRules;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("RuleSet:\n");
		for (RewriterRule rule : rules)
			builder.append(rule.toString() + "\n");
		return builder.toString();
	}
}
