package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RewriterRuleSet {

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
			if (rule.getStmt1().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, true, null, rule.getForwardLinks())) {
				return new ApplicableRule(matches, rule, true);
			}

			if (!rule.isUnidirectional()) {
				if (rule.getStmt2().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, true, null, rule.getBackwardLinks())) {
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
			if (rule.getStmt1().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, false, null, rule.getForwardLinks())) {
				applicableRules.add(new ApplicableRule(matches, rule, true));
				matches = new ArrayList<>();
			}

			if (!rule.isUnidirectional()) {
				if (rule.getStmt2().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, false, null, rule.getBackwardLinks())) {
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

	public static RewriterRuleSet buildSelectionPushdownRuleSet(final RuleContext ctx) {
		RewriterRule ruleRowSelectionPushdown = binaryMatrixIndexingPushdown("RowSelectPushableBinaryInstruction", "rowSelect", ctx);
		RewriterRule ruleColSelectionPushdown = binaryMatrixIndexingPushdown("ColSelectPushableBinaryInstruction", "colSelect", ctx);

		RewriterRule ruleEliminateMultipleRowSelects = ruleEliminateMultipleSelects("rowSelect", ctx);
		RewriterRule ruleEliminateMultipleColSelects = ruleEliminateMultipleSelects("colSelect", ctx);

		RewriterRule ruleOrderRowColSelect = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction("rowSelect")
				.addOp("A")
				.ofType("MATRIX")
				.addOp("h")
				.ofType("INT")
				.addOp("i")
				.ofType("INT")
				.as("rs")
				.withInstruction("colSelect")
				.addExistingOp("rs")
				.addOp("j")
				.ofType("INT")
				.addOp("k")
				.ofType("INT")
				.asRootInstruction()
				.toInstruction("colSelect")
				.addExistingOp("A")
				.addExistingOp("j")
				.addExistingOp("k")
				.as("rs")
				.toInstruction("rowSelect")
				.addExistingOp("rs")
				.addExistingOp("h")
				.addExistingOp("i")
				.asRootInstruction()
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleRowSelectionPushdown);
		rules.add(ruleColSelectionPushdown);
		rules.add(ruleEliminateMultipleRowSelects);
		rules.add(ruleEliminateMultipleColSelects);
		rules.add(ruleOrderRowColSelect);

		return new RewriterRuleSet(ctx, rules);
	}

	/*public static RewriterRuleSet buildSelectionSimplification(final RuleContext ctx) {
		RewriterRule sel1 = new RewriterRuleBuilder(ctx)
				.withInstruction("RowSelectPushableBinaryInstruction")
				.addOp("A")
				.ofType("MATRIX")
				.addOp("B")
				.ofType("MATRIX")
				.as("rs")
				.withInstruction("ColSelectPushableBinaryInstruction");
	}*/

	private static RewriterRule binaryMatrixIndexingPushdown(String instrName, String selectFunc, final RuleContext ctx) {
		return new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction(instrName) // This is more a class of instructions
				.addOp("A")
				.ofType("MATRIX")
				.addOp("B")
				.ofType("MATRIX")
				.as("A + B")
				.withInstruction(selectFunc)
				.addExistingOp("A + B")
				.addOp("i")
				.ofType("INT")
				.addOp("j")
				.ofType("INT")
				.as("res")
				.asRootInstruction()
				.toInstruction(selectFunc)
				.addExistingOp("A")
				.addExistingOp("i")
				.addExistingOp("j")
				.as(selectFunc + "(A,i,j)")
				.toInstruction(selectFunc)
				.addExistingOp("B")
				.addExistingOp("i")
				.addExistingOp("j")
				.as(selectFunc + "(B,i,j)")
				.toInstruction(instrName)
				.addExistingOp(selectFunc + "(A,i,j)")
				.addExistingOp(selectFunc + "(B,i,j)")
				.as("res")
				.asRootInstruction()
				.link("A + B", "res", RewriterStatement::transferMeta)
				.linkManyUnidirectional("res", List.of(selectFunc + "(A,i,j)", selectFunc + "(B,i,j)"), RewriterStatement::transferMeta, true)
				.build();
	}

	private static RewriterRule ruleEliminateMultipleSelects(String selectFunc, final RuleContext ctx) {
		return new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction(selectFunc)
				.addOp("A")
				.ofType("MATRIX")
				.addOp("i")
				.ofType("INT")
				.addOp("j")
				.ofType("INT")
				.as("tmp1")
				.withInstruction(selectFunc)
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
				.toInstruction(selectFunc)
				.addExistingOp("A")
				.addExistingOp("max(i,k)")
				.addExistingOp("min(j,l)")
				.asRootInstruction()
				.build();
	}
}
