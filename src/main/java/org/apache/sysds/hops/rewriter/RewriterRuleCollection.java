package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
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
						.iff(match -> match.getMatchRoot().getOperands().get(0).isLiteral()
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
						.iff(match -> match.getMatchRoot().getOperands().get(1).isLiteral()
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
				.iff(match -> match.getMatchRoot().getOperands().get(0).isLiteral()
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


		// Matrix Multiplication
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("%*%(A, B)", hooks)
				.toParsedStatement("$4:_m($1:_idx(1, nrow(A)), $2:_idx(1, ncol(B)), sum($5:_m($3:_idx(1, ncol(A)), 1, *([](A, $1, $3), [](B, $3, $2)))))", hooks)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(3).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(4).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
					stmt.getOperands().get(1).unsafePutMeta("ownerId", id);
				}, true) // Assumes it will never collide
				.apply(hooks.get(5).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true) // Assumes it will never collide
				.build()
		);

		// E.g. A + B
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("$1:ElementWiseInstruction(A,B)", hooks)
				.toParsedStatement("$7:_m($2:_idx(1, $5:nrow(A)), $3:_idx(1, $6:ncol(A)), $4:ElementWiseInstruction([](A, $2, $3), [](B, $2, $3)))", hooks)
				.iff(match -> {
					return match.getMatchParent() == null || match.getMatchParent().getMeta("dontExpand") == null;
				}, true)
				.link(hooks.get(1).getId(), hooks.get(4).getId(), RewriterStatement::transferMeta)

				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(3).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true)
				.apply(hooks.get(7).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
					stmt.getOperands().get(1).unsafePutMeta("ownerId", id);
				}, true) // Assumes it will never collide
				//.apply(hooks.get(5).getId(), stmt -> stmt.unsafePutMeta("dontExpand", true), true)
				//.apply(hooks.get(6).getId(), stmt -> stmt.unsafePutMeta("dontExpand", true), true)
				.build()
		);

		List.of("$2:_m(i, j, v1), v2", "v1, $2:_m(i, j, v2)").forEach(s -> {
			rules.add(new RewriterRuleBuilder(ctx)
					.setUnidirectional(true)
					.parseGlobalVars("MATRIX:A,B")
					.parseGlobalVars("LITERAL_INT:1")
					.parseGlobalVars("INT:i,j")
					.parseGlobalVars("FLOAT:v1,v2")
					.withParsedStatement("$1:ElementWiseInstruction(" + s + ")", hooks)
					.toParsedStatement("$3:_m(i, j, $4:ElementWiseInstruction(v1, v2))", hooks)
					.link(hooks.get(1).getId(), hooks.get(4).getId(), RewriterStatement::transferMeta)
					.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
					.build()
			);
		});

		// Trace(A)
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("trace(A)", hooks)
				.toParsedStatement("sum($3:_m($1:_idx(1, $2:nrow(A)), 1, [](A, $1, $1)))", hooks)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("dontExpand", true), true)
				.apply(hooks.get(3).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
					stmt.getOperands().get(1).unsafePutMeta("ownerId", id);
				}, true)
				.build()
		);

		// t(A)
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("t(A)", hooks)
				.toParsedStatement("$3:_m($1:_idx(1, ncol(A)), $2:_idx(1, nrow(A)), [](A, $2, $1))", hooks)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true)
				.apply(hooks.get(3).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
					stmt.getOperands().get(1).unsafePutMeta("ownerId", id);
				}, true)
				.build()
		);

		// sum(A) = sum(_m($1:_idx(1, nrow(A)), 1, sum(_m($2:_idx(1, ncol(A)), 1, [](A, $1, $2)))))
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("sum(A)", hooks)
				.toParsedStatement("sum($3:_m($1:_idx(1, nrow(A)), 1, sum($4:_m($2:_idx(1, ncol(A)), 1, [](A, $1, $2)))))", hooks)
				.iff(match -> {
					RewriterStatement meta = (RewriterStatement) match.getMatchRoot().getOperands().get(0).getMeta("ncol");

					if (meta == null)
						throw new IllegalArgumentException("Column meta should not be null: " + match.getMatchRoot().getOperands().get(0).toString(ctx));

					return !meta.isLiteral() || ((int)meta.getLiteral()) != 1;
				}, true)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true)
				.apply(hooks.get(3).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.apply(hooks.get(4).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.build()
		);

		// rowSums(A) -> _m($1:_idx(1, nrow(A)), 1, sum(_m($2:_idx(1, ncol(A)), 1, [](A, $1, $2)))
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("rowSums(A)", hooks)
				.toParsedStatement("$3:_m($1:_idx(1, nrow(A)), 1, sum($4:_m($2:_idx(1, ncol(A)), 1, [](A, $1, $2))))", hooks)
				.iff(match -> {
					RewriterStatement meta = (RewriterStatement) match.getMatchRoot().getOperands().get(0).getMeta("ncol");

					if (meta == null)
						throw new IllegalArgumentException("Column meta should not be null: " + match.getMatchRoot().getOperands().get(0).toString(ctx));

					return !meta.isLiteral() || ((int)meta.getLiteral()) != 1;
				}, true)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true)
				.apply(hooks.get(3).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.apply(hooks.get(4).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.build()
		);

		// rowSums(A) -> _m($1:_idx(1, ncol(A)), 1, sum(_m($2:_idx(1, nrow(A)), 1, [](A, $2, $1)))
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("colSums(A)", hooks)
				.toParsedStatement("$3:_m(1, $1:_idx(1, ncol(A)), sum($4:_m($2:_idx(1, nrow(A)), 1, [](A, $2, $1))))", hooks)
				.iff(match -> {
					RewriterStatement meta = (RewriterStatement) match.getMatchRoot().getOperands().get(0).getMeta("ncol");

					if (meta == null)
						throw new IllegalArgumentException("Column meta should not be null: " + match.getMatchRoot().getOperands().get(0).toString(ctx));

					return !meta.isLiteral() || ((int)meta.getLiteral()) != 1;
				}, true)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true)
				.apply(hooks.get(3).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(1).unsafePutMeta("ownerId", id);
				}, true)
				.apply(hooks.get(4).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("_idx(1, 1)", hooks)
				.toParsedStatement("$1:1", hooks)
				.build()
		);

		// TODO: Continue
		// Scalars dependent on matrix to index streams
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("sum(A)", hooks)
				.toParsedStatement("sum($3:_idxExpr($1:_idx(1, nrow(A)), $4:_idxExpr($2:_idx(1, ncol(A)), [](A, $1, $2))))", hooks)
				.apply(hooks.get(1).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true) // Assumes it will never collide
				.apply(hooks.get(2).getId(), stmt -> stmt.unsafePutMeta("idxId", UUID.randomUUID()), true)
				.apply(hooks.get(3).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.apply(hooks.get(4).getId(), stmt -> {
					UUID id = UUID.randomUUID();
					stmt.unsafePutMeta("ownerId", id);
					stmt.getOperands().get(0).unsafePutMeta("ownerId", id);
				}, true)
				.build()
		);
	}

	// TODO: Big issue when having multiple references to the same sub-dag
	public static void pushdownStreamSelections(final List<RewriterRule> rules, final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx, "Element selection pushdown")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,j,k,l,m")
				.parseGlobalVars("FLOAT:v")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("[]($1:_m(h, i, v), l, m)", hooks)
				.toParsedStatement("as.scalar($2:_m(l, m, v))", hooks)
				/*.iff(match -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands().get(0).getOperands();
					return ops.get(0).isInstruction()
							&& ops.get(1).isInstruction()
							&& ops.get(0).trueTypedInstruction(ctx).equals("_idx(INT,INT)")
							&& ops.get(1).trueTypedInstruction(ctx).equals("_idx(INT,INT)");
				}, true)*/
				.linkUnidirectional(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					RewriterStatement.transferMeta(lnk);
					/*UUID ownerId = (UUID)lnk.newStmt.get(0).getMeta("ownerId");
					System.out.println("OwnerId: " + ownerId);
					lnk.newStmt.get(0).getOperands().get(0).unsafePutMeta("ownerId", ownerId);
					lnk.newStmt.get(0).getOperands().get(0).unsafePutMeta("idxId", UUID.randomUUID());
					lnk.newStmt.get(0).getOperands().get(1).unsafePutMeta("ownerId", ownerId);
					lnk.newStmt.get(0).getOperands().get(1).unsafePutMeta("idxId", UUID.randomUUID());*/

					// TODO: Big issue when having multiple references to the same sub-dag
					for (int idx = 0; idx < 2; idx++) {
						RewriterStatement oldRef = lnk.oldStmt.getOperands().get(idx);
						RewriterStatement newRef = lnk.newStmt.get(0).getOperands().get(idx);

						// Replace all references to h with
						lnk.newStmt.get(0).getOperands().get(2).forEachPreOrder((el, parent, pIdx) -> {
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

		rules.add(new RewriterRuleBuilder(ctx, "Selection pushdown")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:h,i,j,k,l,m")
				.parseGlobalVars("FLOAT:v")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("[]($1:_m(h, i, v), j, k, l, m)", hooks)
				.toParsedStatement("$2:_m(_idx(j, l), _idx(k, m), v)", hooks) // Assuming that selections are valid
				/*.iff(match -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands().get(0).getOperands();
					return ops.get(0).isInstruction()
							&& ops.get(1).isInstruction()
							&& ops.get(0).trueTypedInstruction(ctx).equals("_idx(INT,INT)")
							&& ops.get(1).trueTypedInstruction(ctx).equals("_idx(INT,INT)");
				}, true)*/
				.linkUnidirectional(hooks.get(1).getId(), hooks.get(2).getId(), lnk -> {
					// TODO: Big issue when having multiple references to the same sub-dag
					// BUT: This should usually not happen if indices are never referenced
					RewriterStatement.transferMeta(lnk);
					/*UUID ownerId = (UUID)lnk.newStmt.get(0).getMeta("ownerId");
					lnk.newStmt.get(0).getOperands().get(0).unsafePutMeta("ownerId", ownerId);
					lnk.newStmt.get(0).getOperands().get(0).unsafePutMeta("idxId", UUID.randomUUID());
					lnk.newStmt.get(0).getOperands().get(1).unsafePutMeta("ownerId", ownerId);
					lnk.newStmt.get(0).getOperands().get(1).unsafePutMeta("idxId", UUID.randomUUID());*/

					//if (ownerId == null)
						//throw new IllegalArgumentException();

					for (int idx = 0; idx < 2; idx++) {
						RewriterStatement oldRef = lnk.oldStmt.getOperands().get(idx);
						RewriterStatement newRef = lnk.newStmt.get(0).getOperands().get(idx);

						// Replace all references to h with
						lnk.newStmt.get(0).getOperands().get(2).forEachPreOrder((el, parent, pIdx) -> {
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

		rules.add(new RewriterRuleBuilder(ctx, "Eliminate scalar matrices")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("as.scalar(_m(i, j, v))", hooks)
				.toParsedStatement("v", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx, "_m(i::<const>, j::<const>, v) => v")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, v)", hooks)
				.toParsedStatement("v", hooks)
				.iff(match -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();

					boolean matching = (!ops.get(0).isInstruction() || !ops.get(0).trueInstruction().equals("_idx") || ops.get(0).getMeta("ownerId") != match.getMatchRoot().getMeta("ownerId"))
							&& (!ops.get(1).isInstruction() || !ops.get(1).trueInstruction().equals("_idx") || ops.get(1).getMeta("ownerId") != match.getMatchRoot().getMeta("ownerId"));

					return matching;
				}, true)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx, "_idxExpr(i::<const>, v) => v")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_idxExpr(i, v)", hooks)
				.toParsedStatement("v", hooks)
				.iff(match -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();

					boolean matching = (!ops.get(0).isInstruction() || !ops.get(0).trueInstruction().equals("_idx") || ops.get(0).getMeta("ownerId") != match.getMatchRoot().getMeta("ownerId"))
							&& (!ops.get(1).isInstruction() || !ops.get(1).trueInstruction().equals("_idx") || ops.get(1).getMeta("ownerId") != match.getMatchRoot().getMeta("ownerId"));

					return matching;
				}, true)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx, "_idxExpr(i::<const>, v) => v")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT*:v")
				.withParsedStatement("_idxExpr(i, v)", hooks)
				.toParsedStatement("v", hooks)
				.iff(match -> {
					List<RewriterStatement> ops = match.getMatchRoot().getOperands();

					boolean matching = (!ops.get(0).isInstruction() || !ops.get(0).trueInstruction().equals("_idx") || ops.get(0).getMeta("ownerId") != match.getMatchRoot().getMeta("ownerId"))
							&& (!ops.get(1).isInstruction() || !ops.get(1).trueInstruction().equals("_idx") || ops.get(1).getMeta("ownerId") != match.getMatchRoot().getMeta("ownerId"));

					return matching;
				}, true)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx, "_idxExpr(i, sum(...)) => sum(_idxExpr(i, ...))")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("$1:_idxExpr(i, sum(v))", hooks)
				.toParsedStatement("sum($2:_idxExpr(i, v))", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx, "_idxExpr(i, sum(...)) => sum(_idxExpr(i, ...))")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT*:v")
				.withParsedStatement("$1:_idxExpr(i, sum(v))", hooks)
				.toParsedStatement("sum($2:_idxExpr(i, v))", hooks)
				.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
				.build()
		);

		RewriterUtils.buildBinaryPermutations(List.of("FLOAT"), (t1, t2) -> {
			// TODO: This probably first requires pulling out invariants of this idxExpr
			rules.add(new RewriterRuleBuilder(ctx, "ElementWiseInstruction(sum(_idxExpr(i, ...)), sum(_idxExpr(j, ...))) => _idxExpr(i, _idxExpr(j, sum(...))")
					.setUnidirectional(true)
					.parseGlobalVars("MATRIX:A,B")
					.parseGlobalVars("INT:i,j")
					.parseGlobalVars(t1 + ":v1")
					.parseGlobalVars(t2 + ":v2")
					.withParsedStatement("$1:ElementWiseInstruction(sum($2:_idxExpr(i, v1)), sum($3:_idxExpr(j, v2)))", hooks)
					.toParsedStatement("sum($4:_idxExpr(i, $5:_idxExpr(j, $6:ElementWiseInstruction(v1, v2))))", hooks)
					.link(hooks.get(1).getId(), hooks.get(6).getId(), RewriterStatement::transferMeta)
					.link(hooks.get(2).getId(), hooks.get(4).getId(), RewriterStatement::transferMeta)
					.link(hooks.get(3).getId(), hooks.get(5).getId(), RewriterStatement::transferMeta)
					.build()
			);
		});



		rules.add(new RewriterRuleBuilder(ctx, "sum(sum(v)) => sum(v)")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("sum(sum(v))", hooks)
				.toParsedStatement("sum(v)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx, "sum(sum(v)) => sum(v)")
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT*:v")
				.withParsedStatement("sum(sum(v))", hooks)
				.toParsedStatement("sum(v)", hooks)
				.build()
		);
	}

	public static void flattenOperations(final List<RewriterRule> rules, final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		RewriterUtils.buildBinaryPermutations(List.of("INT", "INT..."), (t1, t2) -> {
			rules.add(new RewriterRuleBuilder(ctx)
					.setUnidirectional(true)
					.parseGlobalVars(t1 + ":i")
					.parseGlobalVars(t2 + ":j")
					.parseGlobalVars("FLOAT:v")
					.withParsedStatement("$1:_idxExpr(i, $2:_idxExpr(j, v))", hooks)
					.toParsedStatement("$3:_idxExpr(argList(i, j), v)", hooks)
					.link(hooks.get(1).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
					.apply(hooks.get(3).getId(), (stmt, match) -> {
						UUID newOwnerId = (UUID)stmt.getMeta("ownerId");

						if (newOwnerId == null)
							throw new IllegalArgumentException();

						stmt.getOperands().get(0).getOperands().get(1).unsafePutMeta("ownerId", newOwnerId);
					}, true)
					.build());
		});

		RewriterUtils.buildBinaryPermutations(List.of("MATRIX", "INT", "FLOAT", "BOOL"), (t1, t2) -> {
			if (RewriterUtils.convertibleType(t1, t2) != null) {
				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t1 + ":A")
						.parseGlobalVars(t2 + ":B")
						.withParsedStatement("$1:FusableBinaryOperator(A,B)", hooks)
						.toParsedStatement("$2:FusedOperator(argList(A,B))", hooks)
						.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
						.build());

				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t1 + "...:A")
						.parseGlobalVars(t2 + ":B")
						.withParsedStatement("$1:FusableBinaryOperator($2:FusedOperator(A), B)", hooks)
						.toParsedStatement("$3:FusedOperator(argList(A, B))", hooks)
						.iff(match -> {
							return match.getMatchRoot().trueInstruction().equals(match.getMatchRoot().getOperands().get(0).trueInstruction());
						}, true)
						.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
						.build());

				rules.add(new RewriterRuleBuilder(ctx)
						.setUnidirectional(true)
						.parseGlobalVars(t1 + "...:A")
						.parseGlobalVars(t2 + ":B")
						.withParsedStatement("$1:FusableBinaryOperator(B, $2:FusedOperator(A))", hooks)
						.toParsedStatement("$3:FusedOperator(argList(B, A))", hooks)
						.iff(match -> {
							return match.getMatchRoot().trueInstruction().equals(match.getMatchRoot().getOperands().get(0).trueInstruction());
						}, true)
						.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
						.build());

				List.of(t1, t1 + "...").forEach(t -> {
					rules.add(new RewriterRuleBuilder(ctx)
							.setUnidirectional(true)
							.parseGlobalVars(t1 + ":A,B")
							.parseGlobalVars(t + ":C")
							.withParsedStatement("$1:FusedOperator(argList($2:FusableBinaryOperator(A, B), C))", hooks)
							.toParsedStatement("$3:FusedOperator(argList(argList(A, B), C))", hooks)
							.iff(match -> {
								return match.getMatchRoot().trueInstruction().equals(match.getMatchRoot().getOperands().get(0).getOperands().get(0).trueInstruction());
							}, true)
							.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
							.build());

					rules.add(new RewriterRuleBuilder(ctx)
							.setUnidirectional(true)
							.parseGlobalVars(t1 + ":A,B")
							.parseGlobalVars(t + ":C")
							.withParsedStatement("$1:FusedOperator(argList(C, $2:FusableBinaryOperator(A, B)))", hooks)
							.toParsedStatement("$3:FusedOperator(argList(C, argList(A, B)))", hooks)
							.iff(match -> {
								return match.getMatchRoot().trueInstruction().equals(match.getMatchRoot().getOperands().get(0).getOperands().get(1).trueInstruction());
							}, true)
							.link(hooks.get(2).getId(), hooks.get(3).getId(), RewriterStatement::transferMeta)
							.build());
				});

			}
		});

	}

	/**
	 * THIS MUST BE EXECUTED PRE-ORDER (e.g. children HAVE to be ordered first)
	 *
	 * How two expressions are compared:
	 * I   By typed instruction name (type-name for datatypes) [e.g. +(INT,INT) > *(INT,INT) > /(INT,INT)]
	 * II  If it is a literal
	 * III Other meta properties if available (e.g. nrow, ncol)
	 *
	 * For distributive instructions:
	 * I  Expand [(a+b)*c = a*c + b*c]
	 *
	 * For commutative instructions:
	 * I  Sort by children
	 *
	 * For associative instructions:
	 * I  Left to right (must be after sorting commutative instructions)
	 *
	 * For stream expressions:
	 * I  Index reference count determines outer expression
	 * II First occurring index
	 * @param rules
	 * @param ctx
	 */
	public static void canonicalizeInstructionOrder(final List<RewriterRule> rules, final RuleContext ctx) {

	}

	public static void collapseStreamingExpressions(final List<RewriterRule> rules, final RuleContext ctx) {

		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("sum(_m(_idx(1, nrow(A)), 1, sum(_m(_idx(1, ncol(A)), 1, [](A, i, j)))))", hooks)
				.toParsedStatement("sum(A)", hooks)
				.build()
		);

		RewriterUtils.buildBinaryPermutations(List.of("INT", "FLOAT", "BOOL"), (t1, t2) -> {
			rules.add(new RewriterRuleBuilder(ctx)
					.setUnidirectional(true)
					.parseGlobalVars("MATRIX:A,B")
					.parseGlobalVars("INT:i,j")
					.parseGlobalVars("FLOAT:v1,v2")
					.withParsedStatement("$3:_m(i, j, $1:ElementWiseInstruction(v1, v2))", hooks)
					.toParsedStatement("$2:ElementWiseInstruction($4:_m(i, j, v1), $5:_m(i, j, v2))", hooks)
					.link(hooks.get(1).getId(), hooks.get(2).getId(), RewriterStatement::transferMeta)
					.linkManyUnidirectional(hooks.get(3).getId(), List.of(hooks.get(4).getId(), hooks.get(5).getId()), link -> {
						RewriterStatement.transferMeta(link);

						// Now detach the reference for the second matrix stream

						UUID newId = UUID.randomUUID();
						link.newStmt.get(1).unsafePutMeta("ownerId", newId);
						RewriterStatement idxI = link.newStmt.get(1).getOperands().get(0).copyNode();
						RewriterStatement idxJ = link.newStmt.get(1).getOperands().get(1).copyNode();
						UUID oldIId = (UUID)idxI.getMeta("idxId");
						UUID oldJId = (UUID)idxJ.getMeta("idxId");
						idxI.unsafePutMeta("idxId", UUID.randomUUID());
						idxI.unsafePutMeta("ownerId", newId);
						idxJ.unsafePutMeta("idxId", UUID.randomUUID());
						idxJ.unsafePutMeta("ownerId", newId);

						RewriterUtils.replaceReferenceAware(link.newStmt.get(1), stmt -> {
							UUID idxId = (UUID) stmt.getMeta("idxId");
							if (idxId != null) {
								if (idxId.equals(oldIId))
									return idxI;
								else if (idxId.equals(oldJId))
									return idxJ;
							}

							return null;
						});
					}, true)
					.build()
			);
		});



		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:a,b,c,d")
				.parseGlobalVars("FLOAT:v")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("_m($1:_idx(a, b), $2:_idx(c, d), [](A, $1, $2))", hooks)
				.toParsedStatement("A", hooks)
				.iff(match -> {
					RewriterStatement A = match.getMatchRoot().getOperands().get(2).getOperands().get(0);
					RewriterStatement a = match.getMatchRoot().getOperands().get(0).getOperands().get(0);
					RewriterStatement b = match.getMatchRoot().getOperands().get(0).getOperands().get(1);
					RewriterStatement c = match.getMatchRoot().getOperands().get(1).getOperands().get(0);
					RewriterStatement d = match.getMatchRoot().getOperands().get(1).getOperands().get(1);

					if (a.isLiteral() && ((int)a.getLiteral()) == 1
						&& b == A.getMeta("nrow")
						&& c.isLiteral() && ((int)c.getLiteral()) == 1
						&& d == A.getMeta("ncol")) {
						return true;
					}

					return false;
				}, true)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:a,b,c,d")
				.parseGlobalVars("FLOAT:v")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("_m($1:_idx(a, b), $2:_idx(c, d), [](A, $1, $2))", hooks)
				.toParsedStatement("$3:[](A, a, b, c, d)", hooks)
				.build()
		);

		/*rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("LITERAL_INT:1")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, sum($1:ElementWiseInstruction(A, B)))", hooks)
				.toParsedStatement("sum(A)", hooks)
				.build()
		);*/



		// TODO: The rule below only hold true for i = _idx(1, nrow(i)) and j = _idx(1, ncol(i))
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, [](A, j, i))", hooks)
				.toParsedStatement("t(A)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, [](A, i, i))", hooks)
				.toParsedStatement("diag(A)", hooks)
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, [](A, j, j))", hooks)
				.toParsedStatement("diag(A)", hooks)
				.build()
		);
	}

	public static void assertCollapsed(final List<RewriterRule> rules, final RuleContext ctx) {
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		rules.add(new RewriterRuleBuilder(ctx)
				.setUnidirectional(true)
				.parseGlobalVars("MATRIX:A,B")
				.parseGlobalVars("INT:i,j")
				.parseGlobalVars("FLOAT:v")
				.withParsedStatement("_m(i, j, v)", hooks)
				.toParsedStatement("$1:_m(i, j, v)", hooks)
				.iff(match -> {
					throw new IllegalArgumentException("Could not eliminate stream expression: " + match.getMatchRoot().toString(ctx));
				}, true)
				.build()
		);
	}

}