package org.apache.sysds.test.component.codegen.rewrite.functions;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.sysds.hops.rewriter.dml.DMLCodeGenerator;
import org.apache.sysds.hops.rewriter.dml.DMLExecutor;
import org.apache.sysds.hops.rewriter.RewriterRule;
import org.apache.sysds.hops.rewriter.RewriterRuleCreator;
import org.apache.sysds.hops.rewriter.RewriterStatement;
import org.apache.sysds.hops.rewriter.utils.RewriterUtils;
import org.apache.sysds.hops.rewriter.RuleContext;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.UUID;
import java.util.function.Function;

public class DMLCodeGenTest {

	private static RuleContext ctx;
	private static Function<RewriterStatement, RewriterStatement> canonicalConverter;

	@BeforeClass
	public static void setup() {
		ctx = RewriterUtils.buildDefaultContext();
		canonicalConverter = RewriterUtils.buildCanonicalFormConverter(ctx, false);
	}

	@Test
	public void test1() {
		RewriterStatement stmt = RewriterUtils.parse("trace(+(A, t(B)))", ctx, "MATRIX:A,B");
		System.out.println(DMLCodeGenerator.generateDML(stmt));
	}

	@Test
	public void test2() {
		String ruleStr1 = "MATRIX:A\nt(t(A))\n=>\nA";
		String ruleStr2 = "MATRIX:A\nrowSums(t(A))\n=>\nt(colSums(A))";
		RewriterRule rule1 = RewriterUtils.parseRule(ruleStr1, ctx);
		RewriterRule rule2 = RewriterUtils.parseRule(ruleStr2, ctx);

		//RewriterRuleSet ruleSet = new RewriterRuleSet(ctx, List.of(rule1, rule2));
		String sessionId = UUID.randomUUID().toString();
		String validationScript = DMLCodeGenerator.generateRuleValidationDML(rule2, DMLCodeGenerator.EPS, sessionId, ctx);
		System.out.println("Validation script:");
		System.out.println(validationScript);
		MutableBoolean valid = new MutableBoolean(true);
		DMLExecutor.executeCode(validationScript, line -> {
			if (!line.startsWith(sessionId))
				return;

			if (!line.endsWith("valid: TRUE")) {
				DMLExecutor.println("An invalid rule was found!");
				DMLExecutor.println(line);
				valid.setValue(false);
			}
		});

		System.out.println("Exiting...");
		assert valid.booleanValue();
	}

	@Test
	public void test3() {
		String ruleStr2 = "MATRIX:A,B\nt(*(A,t(B)))\n=>\n*(t(A),B)";
		RewriterRule rule2 = RewriterUtils.parseRule(ruleStr2, ctx);

		assert RewriterRuleCreator.validateRuleCorrectnessAndGains(rule2, ctx);
	}

	@Test
	public void test4() {
		// Should already be implemented
		String ruleStr2 = "MATRIX:A,B\nt(+(A,t(B)))\n=>\n+(t(A),B)";
		RewriterRule rule2 = RewriterUtils.parseRule(ruleStr2, ctx);

		assert RewriterRuleCreator.validateRuleCorrectnessAndGains(rule2, ctx);
	}

	@Test
	public void test5() {
		String ruleStr2 = "MATRIX:A\nLITERAL_INT:1,2\n-(+(1,A), 1)\n=>\n*(1,A)";
		RewriterRule rule2 = RewriterUtils.parseRule(ruleStr2, ctx);

		assert RewriterRuleCreator.validateRuleCorrectnessAndGains(rule2, ctx);
	}
	@Test
	public void test6() {
		String ruleStr2 = "MATRIX:?,B\nLITERAL_INT:1,2\n+(?,B)\n=>\n*(1,+(?,B))";
		RewriterRule rule2 = RewriterUtils.parseRule(ruleStr2, ctx);

		assert RewriterRuleCreator.validateRuleCorrectnessAndGains(rule2, ctx);
	}

	@Test
	public void test7() {
		String ruleStr2 = "MATRIX:?,B\nLITERAL_INT:1,2\n+(?,B)\n=>\n*(1,+(?,B))";
		RewriterRule rule2 = RewriterUtils.parseRule(ruleStr2, ctx);

		assert RewriterRuleCreator.validateRuleCorrectnessAndGains(rule2, ctx);
	}

	@Test
	public void test8() {
		// TODO: This rule has been ignored, but why?
		String ruleStr = "MATRIX:8cbda53a-49a8-479f-bf34-baeeb1eb8b0f,is_LT_infinite,flip_pos\n" +
				"\n" +
				"+(%*%(is_LT_infinite,flip_pos),%*%(8cbda53a-49a8-479f-bf34-baeeb1eb8b0f,flip_pos))\n" +
				"=>\n" +
				"%*%(+(8cbda53a-49a8-479f-bf34-baeeb1eb8b0f,is_LT_infinite),flip_pos)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		assert RewriterRuleCreator.validateRuleCorrectnessAndGains(rule, ctx);
	}

	@Test
	public void testRev() {
		String ruleStr = "MATRIX:A\n" +
				"FLOAT:b\n" +
				"\n" +
				"rev(*(rev(A),b))\n" +
				"=>\n" +
				"*(A,b)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert RewriterRuleCreator.validateRuleApplicability(rule, ctx);
	}

	@Test
	public void testFused1() {
		// TODO: This rule has been ignored, but why?
		String ruleStr = "MATRIX:A\nLITERAL_FLOAT:0.0\n" +
				"sum(!=(0.0,A))\n" +
				"=>\n" +
				"_nnz(A)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert RewriterRuleCreator.validateRuleApplicability(rule, ctx);
	}

	@Test
	public void testFused2() {
		// TODO: This rule has been ignored, but why?
		String ruleStr = "MATRIX:A,B\nLITERAL_FLOAT:0.0,1.0\n" +
				"-(0.0, -(*(A,B), 1.0))\n" +
				"=>\n" +
				"1-*(A,B)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert RewriterRuleCreator.validateRuleApplicability(rule, ctx);
	}

	@Test
	public void testFused3() {
		// TODO: This rule has been ignored, but why?
		String ruleStr = "MATRIX:A,B\nLITERAL_FLOAT:0.0,1.0\n" +
				"+(-(A,B),A)\n" +
				"=>\n" +
				"-(*2(A), B)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert RewriterRuleCreator.validateRuleApplicability(rule, ctx);
	}

	@Test
	public void testFused4() {
		String ruleStr = "MATRIX:A,B,C\nLITERAL_FLOAT:0.0,1.0\n" +
				"1-*(A, const(A, 0.0))\n" +
				"=>\n" +
				"const(A, 1.0)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(canonicalConverter.apply(rule.getStmt1()).toParsableString(ctx));
		System.out.println(canonicalConverter.apply(rule.getStmt2()).toParsableString(ctx));

		//assert rule.getStmt1().match(RewriterStatement.MatcherContext.exactMatch(ctx, rule.getStmt2(), rule.getStmt1()));

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert RewriterRuleCreator.validateRuleApplicability(rule, ctx, true, null);
	}

	@Test
	public void testFused5() {
		String ruleStr = "MATRIX:A\n" +
				"LITERAL_FLOAT:0.0\n" +
				"\n" +
				"sum(!=(0.0,A))\n" +
				"=>\n" +
				"_nnz(A)";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(canonicalConverter.apply(rule.getStmt1()).toParsableString(ctx));
		System.out.println(canonicalConverter.apply(rule.getStmt2()).toParsableString(ctx));

		//assert rule.getStmt1().match(RewriterStatement.MatcherContext.exactMatch(ctx, rule.getStmt2(), rule.getStmt1()));

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert RewriterRuleCreator.validateRuleApplicability(rule, ctx, true, null);
	}

	@Test
	public void testTransposeScript() {
		String ruleStr = "MATRIX:A\n" +
				"LITERAL_FLOAT:0.0\n" +
				"\n" +
				"t(t(A))\n" +
				"=>\n" +
				"A";

		RewriterRule rule = RewriterUtils.parseRule(ruleStr, ctx);

		System.out.println(canonicalConverter.apply(rule.getStmt1()).toParsableString(ctx));
		System.out.println(canonicalConverter.apply(rule.getStmt2()).toParsableString(ctx));

		//assert rule.getStmt1().match(RewriterStatement.MatcherContext.exactMatch(ctx, rule.getStmt2(), rule.getStmt1()));

		System.out.println(DMLCodeGenerator.generateRuleValidationDML(rule, "test", ctx));

		assert RewriterRuleCreator.validateRuleCorrectness(rule, ctx);

		assert !RewriterRuleCreator.validateRuleApplicability(rule, ctx, true, null);
	}
}
