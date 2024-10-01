package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.List;
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

		return new RewriterHeuristic(new RewriterRuleSet(ctx, preparationRules));
	}

}
