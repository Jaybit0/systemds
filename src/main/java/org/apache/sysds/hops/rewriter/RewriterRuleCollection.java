package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.apache.sysds.hops.rewriter.RewriterContextSettings.ALL_TYPES;

public class RewriterRuleCollection {

	// Anything that can be substituted with 'a == b'
	public static void addEqualitySubstitutions(final List<RewriterRule> rules, final RuleContext ctx) {
		RewriterUtils.buildBinaryPermutations(List.of("MATRIX", "FLOAT", "INT", "BOOL"), (t1, t2) -> {
			rules.add(new RewriterRuleBuilder(ctx)
					.parseGlobalVars(t1 + ":A")
					.parseGlobalVars(t2 + ":B")
					.withParsedStatement("==(A,B)")
					.toParsedStatement("!(!=(A,B))")
					.build()
			);

			rules.add(new RewriterRuleBuilder(ctx)
					.parseGlobalVars(t1 + ":A")
					.parseGlobalVars(t2 + ":B")
					.withParsedStatement("==(A,B)")
					.toParsedStatement("&(>=(A,B), <=(A,B))")
					.build()
			);

			rules.add(new RewriterRuleBuilder(ctx)
					.parseGlobalVars(t1 + ":A")
					.parseGlobalVars(t2 + ":B")
					.withParsedStatement("==(A,B)")
					.toParsedStatement("!(&(>(A,B), <(A,B)))")
					.build()
			);

			rules.add(new RewriterRuleBuilder(ctx)
					.parseGlobalVars(t1 + ":A")
					.parseGlobalVars(t2 + ":B")
					.parseGlobalVars("LITERAL_FLOAT:0")
					.withParsedStatement("==(A,B)")
					.toParsedStatement("==(-(A,B),0)")
					.build()
			);
		});

		ALL_TYPES.forEach(t -> {
			if (t.equals("MATRIX")) {
				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t + ":A")
						.parseGlobalVars("LITERAL_INT:1")
						.withParsedStatement("==(A,A)")
						.toParsedStatement("matrix(1, nrow(A), ncol(A))")
						.build()
				);

				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars("INT:r,c")
						.parseGlobalVars("LITERAL_INT:1")
						.withParsedStatement("matrix(1, r, c)")
						.toParsedStatement("==($1:_rdMATRIX(r, c),$1)")
						.build()
				);
			} else {
				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t + ":A")
						.parseGlobalVars("LITERAL_BOOL:TRUE")
						.withParsedStatement("==(A,A)")
						.toParsedStatement("TRUE")
						.build()
				);

				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars("LITERAL_BOOL:TRUE")
						.withParsedStatement("TRUE")
						.toParsedStatement("==($1:_rd" + t + "(),$1)")
						.build()
				);
			}
		});
	}

	public static void addBooleAxioms(final List<RewriterRule> rules, final RuleContext ctx) {
		// Identity axioms
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.withParsedStatement("a")
				.toParsedStatement("|(a, FALSE)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.withParsedStatement("a")
				.toParsedStatement("&(a, TRUE)")
				.build()
		);

		// Domination axioms
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.withParsedStatement("TRUE")
				.toParsedStatement("|(_anyBool(), TRUE)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.withParsedStatement("|(a, TRUE)")
				.toParsedStatement("TRUE")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.withParsedStatement("FALSE")
				.toParsedStatement("&(_anyBool(), FALSE)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.withParsedStatement("&(a, FALSE)")
				.toParsedStatement("FALSE")
				.build()
		);

		// Idempotence axioms
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a")
				.withParsedStatement("a")
				.toParsedStatement("|(a, a)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a")
				.withParsedStatement("a")
				.toParsedStatement("&(a, a)")
				.build()
		);

		// Commutativity
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a,b")
				.withParsedStatement("|(a, b)")
				.toParsedStatement("|(b, a)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a,b")
				.withParsedStatement("&(a, b)")
				.toParsedStatement("&(b, a)")
				.build()
		);

		// Associativity
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a,b,c")
				.withParsedStatement("|(|(a, b), c)")
				.toParsedStatement("|(a, |(b, c))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a,b,c")
				.withParsedStatement("&(&(a, b), c)")
				.toParsedStatement("&(a, &(b, c))")
				.build()
		);

		// Distributivity
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a,b,c")
				.withParsedStatement("&(a, |(b, c))")
				.toParsedStatement("|(&(a, b), &(a, c))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a,b,c")
				.withParsedStatement("&(&(a, b), c)")
				.toParsedStatement("&(a, &(b, c))")
				.build()
		);

		// Complementation
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.withParsedStatement("TRUE")
				.toParsedStatement("|($1:_anyBool(), !($1))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.withParsedStatement("|(a, !(a))")
				.toParsedStatement("TRUE")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.withParsedStatement("FALSE")
				.toParsedStatement("&($1:_anyBool(), !($1))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("BOOL:a")
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.withParsedStatement("&(a, !(a))")
				.toParsedStatement("FALSE")
				.build()
		);

		// Double negation
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("BOOL:a")
				.withParsedStatement("a")
				.toParsedStatement("!(!(a))")
				.build()
		);


		/*RewriterUtils.buildBinaryPermutations(List.of("MATRIX", "FLOAT", "INT", "BOOL"), (t1, t2) -> {
			boolean isBool = t1.equals("BOOL") && t2.equals("BOOL");
			// Identity axioms
			rules.add(new RewriterRuleBuilder(ctx)
					.parseGlobalVars(t1 + ":A")
					.parseGlobalVars(t2 + ":B")
					.parseGlobalVars("LITERAL_FLOAT:0")
					.withParsedStatement("!=(A,0)")
					.toParsedStatement("!(!=(A,B))")
					.build()
			);
		});*/
	}

	public static void addImplicitBoolLiterals(final List<RewriterRule> rules, final RuleContext ctx) {
		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("TRUE")
				.toParsedStatement("<(_lower(1), 1)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("TRUE")
				.toParsedStatement(">(_higher(1), 1)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("FALSE")
				.toParsedStatement("<(_higher(1), 1)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("LITERAL_BOOL:FALSE")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("FALSE")
				.toParsedStatement(">(_lower(1), 1)")
				.build()
		);
	}

	public static RewriterHeuristic getHeur(final RuleContext ctx) {
		ArrayList<RewriterRule> preparationRules = new ArrayList<>();

		RewriterUtils.buildBinaryPermutations(ALL_TYPES, (t1, t2) -> {
			Stream.of("&", "|").forEach(expr -> {
				preparationRules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t1 + ":a")
						.parseGlobalVars(t2 + ":b")
						.withParsedStatement(expr + "(a, b)")
						.toParsedStatement(expr + "(_asVar(a), b)")
						.iff((match, lnk) -> match.getMatchRoot().getOperands().get(0).isLiteral()
								|| (match.getMatchRoot().getOperands().get(0).isInstruction()
								&& match.getMatchRoot().getOperands().get(0).trueInstruction().startsWith("_")
								&& !match.getMatchRoot().getOperands().get(0).trueInstruction().equals("_asVar")), true)
						.build()
				);
				preparationRules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t1 + ":a")
						.parseGlobalVars(t2 + ":b")
						.withParsedStatement(expr + "(a, b)")
						.toParsedStatement(expr + "(a, _asVar(b))")
						.iff((match, lnk) -> match.getMatchRoot().getOperands().get(1).isLiteral()
								|| (match.getMatchRoot().getOperands().get(1).isInstruction()
								&& match.getMatchRoot().getOperands().get(1).trueInstruction().startsWith("_")
								&& !match.getMatchRoot().getOperands().get(1).trueInstruction().equals("_asVar")), true)
						.build()
				);
			});
		});

		ALL_TYPES.forEach(t -> preparationRules.add((new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars(t + ":a")
				.withParsedStatement("!(a)")
				.toParsedStatement("!(_asVar(a))")
				.iff((match, lnk) -> match.getMatchRoot().getOperands().get(0).isLiteral()
						|| (match.getMatchRoot().getOperands().get(0).isInstruction()
						&& match.getMatchRoot().getOperands().get(0).trueInstruction().startsWith("_")
						&& !match.getMatchRoot().getOperands().get(0).trueInstruction().equals("_asVar")), true)
				.build()
		)));

		RewriterRuleSet rs = new RewriterRuleSet(ctx, preparationRules);
		rs.accelerate();

		return new RewriterHeuristic(rs, true);
	}

	// E.g. expand A * B -> _m($1:_idx(), 1, nrow(A), _m($2:_idx(), 1, nrow(B), A[$1, $2] * B[$1, $2]))
	public static void expandStreamingExpressions(final List<RewriterRule> rules, final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("$1:ElementWiseInstruction(A,B)", hooks)
				.toParsedStatement("_m($2:_idx(1, nrow(A)), $3:_idx(1, ncol(A)), $4:ElementWiseInstruction([](A, $2, $3), [](B, $2, $3)))", hooks)
				.link(hooks.get(1).getId(), hooks.get(4).getId(), RewriterStatement::transferMeta)
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(3).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.build()
		);
	}

	public static void pushdownStreamSelections(final List<RewriterRule> rules, final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,j,k,l,m")
				.parseGlobalVars("FLOAT:v")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("[]($1:_m(h, i, v), l, m)", hooks)
				.toParsedStatement("$2:_m(l, m, v)", hooks)
				.iff((match, lnk) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands().get(0).getOperands();
					return ops.get(0).isInstruction()
							&& ops.get(1).isInstruction()
							&& ops.get(0).trueTypedInstruction(ctx).equals("_idx(INT,INT)")
							&& ops.get(1).trueTypedInstruction(ctx).equals("_idx(INT,INT)");
				}, true)
				.linkUnidirectional(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					for (int idx = 0; idx < 2; idx++) {
						RewriterStatement oldRef = lnk.oldStmt.getOperands().get(idx);
						RewriterStatement newRef = lnk.newStmt.get(0).getOperands().get(idx);

						// Replace all references to h with
						lnk.newStmt.get(0).getOperands().get(2).forEachInOrder((el, parent, pIdx) -> {
							if (el.getOperands() != null) {
								for (int i = 0; i < el.getOperands().size(); i++) {
									RewriterStatement child = el.getOperands().get(i);
									Object meta = child.getMeta("idxId");

									if (meta instanceof UUID && meta.equals(oldRef.getMeta("idxId")))
										el.getOperands().set(i, newRef);
								}
							}
							return true;
						});

					}
				}, true)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, v)", hooks)
				.toParsedStatement("v", hooks)
				.iff((match, lnk) -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();

					return (!ops.get(0).isInstruction() || !ops.get(0).trueInstruction().equals("_idx"))
							&& (!ops.get(1).isInstruction() || !ops.get(1).trueInstruction().equals("_idx"));
				}, true)
				.build()
		);
	}

}
