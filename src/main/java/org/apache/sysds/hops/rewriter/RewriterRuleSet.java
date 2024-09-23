package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.sysds.utils.Hash;
import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
			//if (rule.getStmt1().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, true, null, rule.getForwardLinks())) {
			if (rule.matchStmt1(instr, matches, true)) {
				return new ApplicableRule(matches, rule, true);
			}

			if (!rule.isUnidirectional()) {
				//if (rule.getStmt2().matchSubexpr(ctx, instr, null, -1, matches, new DualHashBidiMap<>(), true, false, true, null, rule.getBackwardLinks())) {
				if (rule.matchStmt2(instr, matches, true)) {
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
			if (rule.matchStmt1(instr, matches, false)) {
				applicableRules.add(new ApplicableRule(matches, rule, true));
				matches = new ArrayList<>();
			}

			if (!rule.isUnidirectional()) {
				if (rule.matchStmt2(instr, matches, false)) {
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

	public static RewriterRuleSet buildUnfoldAggregations(final RuleContext ctx) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		// TODO: Variance unfolding may lead to worse results
		/*rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.intLiteral("0", 0)
				.withParsedStatement("var(A)", hooks)
				.toParsedStatement("if(==(*(nrows(A), ncols(A)), 1), asMatrix(0), *(/(1,*(nrows(A), ncols(A))), *(-(A, mean(A)), -(A, mean(A)))))", hooks)
				.build()
		);*/

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("mean(A)", hooks)
				.toParsedStatement("/(sum(A), *(nrows(A), ncols(A)))", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("sum(A)", hooks)
				.toParsedStatement("rowSums(colSums(A))", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("colSums(rowSums(A))", hooks)
				.toParsedStatement("rowSums(colSums(A))", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("rowMeans(A)", hooks)
				.toParsedStatement("/(rowSums(A), ncols(A))", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("colMeans(A)", hooks)
				.toParsedStatement("/(colSums(A), nrows(A))", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("rowMeans(A)", hooks)
				.toParsedStatement("/(rowSums(A), ncols(A))", hooks)
				.build()
		);

		return new RewriterRuleSet(ctx, rules);
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

	/**
	 * Expects the order colSelect(rowSelect(...))
	 * @param ctx
	 * @return
	 */
	public static RewriterRuleSet buildCBindElimination(final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks;
		ArrayList<RewriterRule> rules = new ArrayList<>();

		String mappingString1 = "if(&&(_compileTimeIsEqual(A, B), ==(+(i,1),l)),"
				+ "colSelect(A, h, m),"
				+ "$2:CBind(colSelect(A, h, i), colSelect(B, l, m)))";

		/*String mappingString1A = "if(==(+(i,1),l),"
				+ "colSelect(A, h, m),"
				+ "$2:CBind(colSelect(A, h, i), colSelect(A, l, m)))";

		String mappingString2 = "if(&&(&&(&&(_compileTimeIsEqual(A, B), ==(+(k,1),n)),==(h,l)),==(i,m)),"
				+ "index(A, h, m, j, k),"
				+ "$2:CBind(index(A, h, i, j, k), index(B, l, m, n, o)))";

		String mappingString2A = "if(&&(&&(==(+(k,1),n),==(h,l)),==(i,m)),"
				+ "index(A, h, m, j, k),"
				+ "$2:CBind(index(A, h, i, j, k), index(A, l, m, n, o)))";*/

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,l,m")
				.intLiteral("1", 1)
				.withParsedStatement("$1:CBind(colSelect(A, h, i), colSelect(B, l, m))", hooks)
				.toParsedStatement(mappingString1, hooks)
				.iff((match, links) -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);

		/*hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,l,m")
				.intLiteral("1", 1)
				.withParsedStatement("$1:CBind(colSelect(A, h, i), colSelect(A, l, m))", hooks)
				.toParsedStatement(mappingString1A, hooks)
				.iff(match -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,j,k,l,m,n,o")
				.intLiteral("1", 1)
				.withParsedStatement("$1:CBind(index(A, h, i, j, k), index(B, l, m, n, o))", hooks)
				.toParsedStatement(mappingString2, hooks)
				.iff(match -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);

		hooks = new HashMap<>();

		// TODO: This should implicitly be handled by the matcher
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,j,k,l,m,n,o")
				.intLiteral("1", 1)
				.withParsedStatement("$1:CBind(index(A, h, i, j, k), index(A, l, m, n, o))", hooks)
				.toParsedStatement(mappingString2A, hooks)
				.iff(match -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);*/

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildReorderColRowSelect(String newOuter, String newInner, final RuleContext ctx) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,j,k")
				.withParsedStatement(newInner + "(" + newOuter + "(A, h, i), j, k)", hooks)
				.toParsedStatement(newOuter + "(" + newInner + "(A, j, k), h, i)", hooks)
				.build());

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildRBindElimination(final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		ArrayList<RewriterRule> rules = new ArrayList<>();

		String mappingString1 = "if(&&(_compileTimeIsEqual(A, B), ==(+(i,1),l)),"
				+ "rowSelect(A, h, m),"
				+ "$2:RBind(rowSelect(A, h, i), rowSelect(B, l, m)))";

		String mappingString1A = "if(==(+(i,1),l),"
				+ "rowSelect(A, h, m),"
				+ "$2:RBind(rowSelect(A, h, i), rowSelect(A, l, m)))";

		String mappingString2 = "if(&&(&&(&&(_compileTimeIsEqual(A, B), ==(+(i,1),l)),==(j,n)),==(k,o)),"
				+ "index(A, h, m, j, k),"
				+ "$2:RBind(index(A, h, i, j, k), index(B, l, m, n, o)))";

		String mappingString2A = "if(&&(&&(==(+(i,1),l),==(j,n)),==(k,o)),"
				+ "index(A, h, m, j, k),"
				+ "$2:RBind(index(A, h, i, j, k), index(A, l, m, n, o)))";

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,l,m")
				.intLiteral("1", 1)
				.withParsedStatement("$1:RBind(rowSelect(A, h, i), rowSelect(B, l, m))", hooks)
				.toParsedStatement(mappingString1, hooks)
				.iff((match, links) -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);

		hooks = new HashMap<>();

		// TODO: This should implicitly be handled by the matcher
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,l,m")
				.intLiteral("1", 1)
				.withParsedStatement("$1:RBind(rowSelect(A, h, i), rowSelect(A, l, m))", hooks)
				.toParsedStatement(mappingString1A, hooks)
				.iff((match, links) -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,j,k,l,m,n,o")
				.intLiteral("1", 1)
				.withParsedStatement("$1:RBind(index(A, h, i, j, k), index(B, l, m, n, o))", hooks)
				.toParsedStatement(mappingString2, hooks)
				.iff((match, links) -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
				.build()
		);

		hooks = new HashMap<>();

		// TODO: This should implicitly be handled by the matcher
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,j,k,l,m,n,o")
				.intLiteral("1", 1)
				.withParsedStatement("$1:RBind(index(A, h, i, j, k), index(A, l, m, n, o))", hooks)
				.toParsedStatement(mappingString2A, hooks)
				.iff((match, links) -> {
					Object meta = match.getMatchRoot().getMeta("bindChecked");
					return meta == null || (meta instanceof Boolean && !((Boolean)meta));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("bindChecked", true);
				})
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

	public static RewriterRuleSet buildAggregationPushdown(final RuleContext ctx, DualHashBidiMap<String, String> equivalendRowColAggregations) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:FullAdditiveAggregationInstruction($2:FullAdditiveAggregationPushableInstruction(A, B))", hooks)
				.toParsedStatement("$3:FullAdditiveAggregationPushableInstruction($4:FullAdditiveAggregationInstruction(A), $5:FullAdditiveAggregationInstruction(B))", hooks)
				.linkManyUnidirectional(hooks.get(1).getId(), List.of(hooks.get(4).getId(), hooks.get(5).getId()), RewriterStatement::transferMeta, true)
				.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:RowAdditiveAggregationInstruction($2:RowAdditiveAggregationPushableInstruction(A, B))", hooks)
				.toParsedStatement("$3:RowAdditiveAggregationPushableInstruction($4:RowAdditiveAggregationInstruction(A), $5:RowAdditiveAggregationInstruction(B))", hooks)
				.linkManyUnidirectional(hooks.get(1).getId(), List.of(hooks.get(4).getId(), hooks.get(5).getId()), RewriterStatement::transferMeta, true)
				.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:ColAdditiveAggregationInstruction($2:ColAdditiveAggregationPushableInstruction(A, B))", hooks)
				.toParsedStatement("$3:ColAdditiveAggregationPushableInstruction($4:ColAdditiveAggregationInstruction(A), $5:ColAdditiveAggregationInstruction(B))", hooks)
				.linkManyUnidirectional(hooks.get(1).getId(), List.of(hooks.get(4).getId(), hooks.get(5).getId()), RewriterStatement::transferMeta, true)
				.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:FullAggregationInstruction($2:Rearrangement(A))", hooks)
				.toParsedStatement("$3:FullAggregationInstruction(A)", hooks)
				.link(hooks.get(1).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.build()
		);

		// TODO: This works if it is not variance

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:RowAggregationInstruction($2:RowPermutation(A))", hooks)
				.toParsedStatement("$3:RowAggregationInstruction(A)", hooks)
				.link(hooks.get(1).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:ColAggregationInstruction($2:ColPermutation(A))", hooks)
				.toParsedStatement("$3:ColAggregationInstruction(A)", hooks)
				.link(hooks.get(1).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
				.build()
		);

		// TODO: What happens if rowSums(rowSums(A) + rowSums(B))? We need to track the dimensions

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("ColAggregationInstruction($1:ColAggregationInstruction(A))", hooks)
				.toParsedStatement("$2:ColAggregationInstruction(A)", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("RowAggregationInstruction($1:RowAggregationInstruction(A))", hooks)
				.toParsedStatement("$2:RowAggregationInstruction(A)", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);


		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("$1:ColAggregationInstruction(t(A))", hooks)
				.toParsedStatement("t($2:RowAggregationInstruction(A))", hooks)
				.iff((match, links) -> equivalendRowColAggregations.containsValue(match.getMatchRoot().trueTypedInstruction(ctx)), true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), link -> {
					((RewriterInstruction)link.newStmt.get(0)).unsafeSetInstructionName(RewriterUtils.typedToUntypedInstruction(equivalendRowColAggregations.getKey(((RewriterInstruction)link.oldStmt).trueTypedInstruction(ctx))));
				})
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("$1:RowAggregationInstruction(t(A))", hooks)
				.toParsedStatement("t($2:ColAggregationInstruction(A))", hooks)
				.iff((match, links) -> equivalendRowColAggregations.containsKey(match.getMatchRoot().trueTypedInstruction(ctx)), true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), link -> {
					((RewriterInstruction)link.newStmt.get(0)).unsafeSetInstructionName(RewriterUtils.typedToUntypedInstruction(equivalendRowColAggregations.get(((RewriterInstruction)link.oldStmt).trueTypedInstruction(ctx))));
				})
				.build()
		);

		/*hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("rowSums(t(A))", hooks)
				.toParsedStatement("t(colSums(A))", hooks)
				.build()
		);*/

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildElementWiseInstructionPushdown(final RuleContext ctx) {
		// TODO: e.g. t(A) + t(B) = t(A + B)

		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:ElementWiseInstruction(t(A), t(B))", hooks)
				.toParsedStatement("t($2:ElementWiseInstruction(A, B))", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("%*%(t(A), t(B))", hooks)
				.toParsedStatement("%*%(B, A)", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("t($1:ElementWiseInstruction(t(A), B)))", hooks)
				.toParsedStatement("$2:ElementWiseInstruction(A, t(B))", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("t($1:ElementWiseInstruction(A, t(B)))", hooks)
				.toParsedStatement("$2:ElementWiseInstruction(t(A), B))", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);



		// TODO: We must put this at the end

		/*hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("$1:RowAggregationInstruction(A)", hooks)
				.toParsedStatement("if(_compileTimeIsEqual(ncols(A), 1), A, $2:RowAggregationInstruction(A))", hooks)
				.iff((match, lnk) -> {
					Object checked = match.getMatchRoot().getMeta("aggSizeChecked");
					return checked == null || (checked instanceof Boolean && !((Boolean)checked));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("aggSizeChecked", true);
				})
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("$1:ColAggregationInstruction(A)", hooks)
				.toParsedStatement("if(_compileTimeIsEqual(nrows(A), 1), A, $2:ColAggregationInstruction(A))", hooks)
				.iff((match, lnk) -> {
					Object checked = match.getMatchRoot().getMeta("aggSizeChecked");
					return checked == null || (checked instanceof Boolean && !((Boolean)checked));
				}, true)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					lnk.newStmt.get(0).unsafePutMeta("aggSizeChecked", true);
				})
				.build()
		);*/

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildTransposeElimination(final RuleContext ctx) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("t(t(A))", hooks)
				.toParsedStatement("A", hooks)
				.build()
		);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildMetaInstructionSimplification(final RuleContext ctx) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("nrows(SizeSwappingInstruction(A))", hooks)
				.toParsedStatement("ncols(A)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("ncols(SizeSwappingInstruction(A))", hooks)
				.toParsedStatement("nrows(A)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("ncols(ColAggregationInstruction(A))", hooks)
				.toParsedStatement("1", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("nrows(RowAggregationInstruction(A))", hooks)
				.toParsedStatement("1", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("ncols(RowAggregationInstruction(A))", hooks)
				.toParsedStatement("ncols(A)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("nrows(ColAggregationInstruction(A))", hooks)
				.toParsedStatement("nrows(A)", hooks)
				.build()
		);

		hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("$1:SizeInstruction(SizePreservingInstruction(A, B))", hooks)
				.toParsedStatement("_compileTimeSelectLeastExpensive($2:SizeInstruction(A), $3:SizeInstruction(B))", hooks)
				.linkManyUnidirectional(hooks.get(1).getId(), List.of(hooks.get(2).getId(), hooks.get(3).getId()), RewriterStatement::transferMeta, true)
				.build()
		);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildCompileTimeFolding(final RuleContext ctx) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:a,b")
				.intLiteral("1", 1)
				.withParsedStatement("_compileTimeIsEqual($1:a, $2:b)", hooks)
				.toParsedStatement("1", hooks)
				.iff((match, links) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();
					return ops.get(0).isLiteral() && ops.get(1).isLiteral() && ops.get(0).getLiteral().equals(ops.get(1).getLiteral());
				}, true)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.intLiteral("1", 1)
				.withParsedStatement("_compileTimeIsEqual(A, A)", hooks)
				.toParsedStatement("1", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:a")
				.intLiteral("1", 1)
				.withParsedStatement("&&(1, a)", hooks)
				.toParsedStatement("a", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:a")
				.intLiteral("1", 1)
				.withParsedStatement("&&(a, 1)", hooks)
				.toParsedStatement("a", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:a")
				.intLiteral("0", 0)
				.withParsedStatement("&&(0, a)", hooks)
				.toParsedStatement("0", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:a")
				.intLiteral("0", 0)
				.withParsedStatement("&&(a, 0)", hooks)
				.toParsedStatement("0", hooks)
				.build()
		);



		// Eliminate the compileTimeEqualityCheck
		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:a,b")
				.intLiteral("0", 0)
				.withParsedStatement("_compileTimeIsEqual($1:a, $2:b)", hooks)
				.toParsedStatement("0", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.intLiteral("0", 0)
				.withParsedStatement("_compileTimeIsEqual($1:A, $2:B)", hooks)
				.toParsedStatement("0", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.intLiteral("0", 0)
				.withParsedStatement("if(0, A, B)", hooks)
				.toParsedStatement("B", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.intLiteral("1", 1)
				.withParsedStatement("if(1, A, B)", hooks)
				.toParsedStatement("A", hooks)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.intLiteral("1", 1)
				.withParsedStatement("_compileTimeSelectLeastExpensive(A, B)", hooks)
				.toParsedStatement("A", hooks)
				.iff((match, lnk) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();
					return ops.get(0).getCost() >= ops.get(1).getCost();
				}, true)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.intLiteral("1", 1)
				.withParsedStatement("_compileTimeSelectLeastExpensive(A, B)", hooks)
				.toParsedStatement("A", hooks)
				.iff((match, lnk) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();
					return ops.get(0).getCost() < ops.get(1).getCost();
				}, true)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:A,B")
				.intLiteral("1", 1)
				.withParsedStatement("_compileTimeSelectLeastExpensive(A, B)", hooks)
				.toParsedStatement("A", hooks)
				.iff((match, lnk) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();
					return ops.get(0).getCost() >= ops.get(1).getCost();
				}, true)
				.build()
		);

		hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("INT:A,B")
				.intLiteral("1", 1)
				.withParsedStatement("_compileTimeSelectLeastExpensive(A, B)", hooks)
				.toParsedStatement("A", hooks)
				.iff((match, lnk) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();
					return ops.get(0).getCost() < ops.get(1).getCost();
				}, true)
				.build()
		);

		return new RewriterRuleSet(ctx, rules);
	}

	public static RewriterRuleSet buildAggregationFolding(final RuleContext ctx) {
		ArrayList<RewriterRule> rules = new ArrayList<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.withParsedStatement("rowSums(colSums(A))", hooks)
				.toParsedStatement("sum(A)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("/(sum(A), *(nrows(A), ncols(A)))", hooks)
				.toParsedStatement("mean(A)", hooks)
				.build()
		);

		return new RewriterRuleSet(ctx, rules);
	}

	private static RewriterRule binaryMatrixLRIndexingPushdown(String instrName, String selectFuncOrigin, String[] indexingInput, String destSelectFuncL, String[] indexingInputL, String destSelectFuncR, String[] indexingInputR, final RuleContext ctx) {
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
				.build();
	}
}
