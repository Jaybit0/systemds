package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
		RuleContext.currentContext = ctx;
		StringBuilder builder = new StringBuilder("RuleSet:\n");
		for (RewriterRule rule : rules)
			builder.append(rule.toString() + "\n");
		return builder.toString();
	}

	public static RewriterRuleSet buildSelectionBreakup(final RuleContext ctx) {
		/*RewriterRule ruleBreakupSelections = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction("index")
				.addOp("A")
				.ofType("MATRIX")
				.addOp("h")
				.ofType("INT")
				.addOp("i")
				.ofType("INT")
				.addOp("j")
				.ofType("INT")
				.addOp("k")
				.ofType("INT")
				.asRootInstruction()
				.toInstruction("colSelect")
				.addExistingOp("A")
				.addExistingOp("j")
				.addExistingOp("k")
				.as("ir")
				.toInstruction("rowSelect")
				.addExistingOp("ir")
				.addExistingOp("h")
				.addExistingOp("i")
				.asRootInstruction()
				.build();*/

		RewriterRule rule = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,j,k")
				.withParsedStatement("index(A,h,i,j,k)", new HashMap<>())
				.toParsedStatement("rowSelect(colSelect(A,j,k),h,i)", new HashMap<>())
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(rule);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildSelectionPushdownRuleSet(final RuleContext ctx) {
		RewriterRule ruleIdxSelectionRowPushdown = binaryMatrixIndexingPushdown("IdxSelectPushableBinaryInstruction", "rowSelect", ctx);
		RewriterRule ruleIdxSelectionColPushdown = binaryMatrixIndexingPushdown("IdxSelectPushableBinaryInstruction", "colSelect", ctx);
		RewriterRule ruleRowSelectionPushdown = binaryMatrixIndexingPushdown("RowSelectPushableBinaryInstruction", "rowSelect", ctx);
		RewriterRule ruleColSelectionPushdown = binaryMatrixIndexingPushdown("ColSelectPushableBinaryInstruction", "colSelect", ctx);

		RewriterRule ruleRowMMSelectionPushdown = binaryMatrixLRIndexingPushdown("RowSelectMMPushableBinaryInstruction",
				"rowSelect",
				new String[] {"i", "j"},
				"rowSelect",
				new String[] {"i", "j"},
				"colSelect",
				new String[] {"i", "j"},
				ctx);

		RewriterRule ruleColMMSelectionPushdown = binaryMatrixLRIndexingPushdown("ColSelectMMPushableBinaryInstruction",
				"colSelect",
				new String[] {"i", "j"},
				"colSelect",
				new String[] {"i", "j"},
				"rowSelect",
				new String[] {"i", "j"},
				ctx);

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
				.link("result", "rs", RewriterStatement::transferMeta)
				.link("rs", "result", RewriterStatement::transferMeta)
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleIdxSelectionRowPushdown);
		rules.add(ruleIdxSelectionColPushdown);
		rules.add(ruleRowSelectionPushdown);
		rules.add(ruleColSelectionPushdown);
		rules.add(ruleRowMMSelectionPushdown);
		rules.add(ruleColMMSelectionPushdown);
		rules.add(ruleEliminateMultipleRowSelects);
		rules.add(ruleEliminateMultipleColSelects);
		rules.add(ruleOrderRowColSelect);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildRbindCbindSelectionPushdown(final RuleContext ctx) {
		String mappingString =
				"if (<=(i, ncols(A)),"
				+ "if ( <=(j, ncols(A)),"
				+ "colSelect(A, i, j),"
				+ "CBind(colSelect(A,i,ncols(A)),colSelect(B, 0, -(+(i,j), ncols(A)) ))),"
				+ "colSelect(B,-(i,ncols(A)),-(j,ncols(A)))"
				+ ")";

		String mappingString2 =
				"if (<=(i, nrows(A)),"
						+ "if ( <=(j, nrows(A)),"
						+ "rowSelect(A, i, j),"
						+ "RBind(rowSelect(A,i,nrows(A)),rowSelect(B, 0, -(+(i,j), nrows(A)) ))),"
						+ "rowSelect(B,-(i,nrows(A)),-(j,nrows(A)))"
						+ ")";

		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.intLiteral("0", 0)
				.withParsedStatement("colSelect(CBind(A,B),i,j)", hooks)
				.toParsedStatement(mappingString, hooks)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.intLiteral("0", 0)
				.withParsedStatement("rowSelect(RBind(A,B),i,j)", hooks)
				.toParsedStatement(mappingString2, hooks)
				.build()
		);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildSelectionSimplification(final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		RewriterRule ruleSimplify = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,j,k")
				.withParsedStatement("rowSelect(colSelect(A,j,k),h,i)", hooks)
				.toParsedStatement("index(A,h,i,j,k)", hooks)
				/*.withInstruction("colSelect")
				.addOp("A")
				.ofType("MATRIX")
				.addOp("j")
				.ofType("INT")
				.addOp("k")
				.ofType("INT")
				.as("ir")
				.withInstruction("rowSelect")
				.addExistingOp("ir")
				.addOp("h")
				.ofType("INT")
				.addOp("i")
				.ofType("INT")
				.asRootInstruction()
				.toInstruction("index")
				.addExistingOp("A")
				.addExistingOp("h")
				.addExistingOp("i")
				.addExistingOp("j")
				.addExistingOp("k")
				.asRootInstruction()*/
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleSimplify);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildDynamicOpInstructions(final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		RewriterRule ruleFuse1 = new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:FusableBinaryOperator(A,B)", hooks)
				.toParsedStatement("$2:FusedOperator(argList(A,B))", hooks)
				/*.withInstruction("FusableBinaryOperator")
				.addOp("A")
				.ofType("MATRIX")
				.addOp("B")
				.ofType("MATRIX")
				.asRootInstruction()
				.addDynamicOpListInstr("matrixList", "MATRIX...", false, "A", "B")
				.as("[A,B]")
				.toInstruction("FusedOperator")
				.addExistingOp("[A,B]")
				.asRootInstruction()
				.link("result", "result", RewriterStatement::transferMeta)*/
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleFuse1);

		return new RewriterRuleSet(ctx, rules);
	}

	private static RewriterRule binaryMatrixLRIndexingPushdown(String instrName, String selectFuncOrigin, String[] indexingInput, String destSelectFuncL, String[] indexingInputL, String destSelectFuncR, String[] indexingInputR, final RuleContext ctx) {
		/*return new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.withInstruction(instrName) // This is more a class of instructions
				.addOp("A")
				.ofType("MATRIX")
				.addOp("B")
				.ofType("MATRIX")
				.as("A + B")
				.withInstruction(selectFuncOrigin)
				.addExistingOp("A + B")
				.addOp(indexingInput[0]).ofType("INT")
				.addOp(indexingInput[1]).ofType("INT")
				.as("res")
				.asRootInstruction()
				.toInstruction(destSelectFuncL)
				.addExistingOp("A")
				.addExistingOp(indexingInputL[0])
				.addExistingOp(indexingInputL[1])
				.as(destSelectFuncL + "(A...)")
				.toInstruction(destSelectFuncR)
				.addExistingOp("B")
				.addExistingOp(indexingInputR[0])
				.addExistingOp(indexingInputL[0])
				.as(destSelectFuncR + "(B...)")
				.toInstruction(instrName)
				.addExistingOp(destSelectFuncL + "(A...)")
				.addExistingOp(destSelectFuncR + "(B...)")
				.as("res")
				.asRootInstruction()
				.link("A + B", "res", RewriterStatement::transferMeta)
				.linkManyUnidirectional("res", List.of(destSelectFuncL + "(A...)", destSelectFuncR + "(B...)"), RewriterStatement::transferMeta, true)
				.build();*/

		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		return new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:" + String.join(",", indexingInput))
				.withParsedStatement("$1:" + selectFuncOrigin + "($2:" + instrName + "(A,B),i,j)", hooks)
				.toParsedStatement("$3:" + instrName + "($4:" + destSelectFuncL + "(A," + indexingInputL[0] + "," + indexingInputL[1] + "),$5:" + destSelectFuncR + "(B," + indexingInputR[0] + "," + indexingInputR[1] + "))", hooks)
				.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.linkManyUnidirectional(hooks.get(1).getId(), List.of(hooks.get(4).getId(), hooks.get(5).getId()), RewriterStatement::transferMeta, true)
				.build();
	}

	private static RewriterRule binaryMatrixIndexingPushdown(String instrName, String selectFunc, final RuleContext ctx) {
		/*return new RewriterRuleBuilder(ctx)
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
				.build();*/

		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		return new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.withParsedStatement("$1:" + selectFunc + "($2:" + instrName + "(A,B),i,j)", hooks)
				.toParsedStatement("$3:" + instrName + "($4:" + selectFunc + "(A,i,j),$5:" + selectFunc + "(B,i,j))", hooks)
				.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.linkManyUnidirectional(hooks.get(1).getId(), List.of(hooks.get(4).getId(), hooks.get(5).getId()), RewriterStatement::transferMeta, true)
				.build();
	}

	private static RewriterRule ruleEliminateMultipleSelects(String selectFunc, final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		return new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:i,j,k,l")
				.withParsedStatement(selectFunc + "(" + selectFunc + "(A,i,j),k,l)", hooks)
				.toParsedStatement(selectFunc + "(A,max(i,k),min(j,l))", hooks)
				/*.withInstruction(selectFunc)
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
				.asRootInstruction()*/
				.build();
	}
}
