package org.apache.sysds.test.component.codegen.rewrite;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.sysds.api.DMLScript;
import org.apache.sysds.hops.Hop;
import org.apache.sysds.hops.LiteralOp;
import org.apache.sysds.hops.OptimizerUtils;
import org.apache.sysds.hops.rewriter.RewriterContextSettings;
import org.apache.sysds.hops.rewriter.RewriterDatabase;
import org.apache.sysds.hops.rewriter.RewriterRule;
import org.apache.sysds.hops.rewriter.RewriterRuleBuilder;
import org.apache.sysds.hops.rewriter.RewriterRuleSet;
import org.apache.sysds.hops.rewriter.RewriterStatement;
import org.apache.sysds.hops.rewriter.RewriterUtils;
import org.apache.sysds.hops.rewriter.RuleContext;
import org.apache.sysds.parser.DMLProgram;
import org.apache.sysds.parser.StatementBlock;
import org.junit.Test;
import scala.Tuple4;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class TestRewriteExecution {

	private enum DataType {
		FLOAT, INT, MATRIX
	};

	private int currentHopCount = 0;
	private RewriterStatement lastStatement = null;
	private List<Hop> lastHops = null;
	private RewriterStatement nextStatement = null;
	private List<Hop> nextHops = null;
	private List<Tuple4<RewriterStatement, List<Hop>, RewriterStatement, List<Hop>>> costIncreasingTransformations = new ArrayList<>();

	private BiConsumer<DMLProgram, String> interceptor = (prog, phase) -> {
		if (!phase.equals("HOPRewrites"))
			return;
		//int hopCtr = 0;
		for (StatementBlock sb : prog.getStatementBlocks()) {
			int hopCount = sb.getHops() == null ? 0 : sb.getHops().stream().mapToInt(this::countHops).sum();
			nextHops = sb.getHops();

			if (lastStatement == null) {
				currentHopCount = hopCount;
			} else if (hopCount > currentHopCount) {
				costIncreasingTransformations.add(new Tuple4<>(lastStatement, lastHops, nextStatement, nextHops));
			}

			//System.out.println(phase + "-Size: " + hopCount);
			//System.out.println("==> " + sb);
			return;
		}
	};

	private int countHops(List<Hop> hops) {
		return hops.stream().mapToInt(this::countHops).sum();
	}

	private int countHops(Hop hop) {
		if (hop instanceof LiteralOp)
			return 0;
		int curr = 1;
		for (Hop child : hop.getInput())
			curr += countHops(child);
		return curr;
	}

	@Test
	public void test() {
		System.out.println("OptLevel:" + OptimizerUtils.getOptLevel().toString());
		System.out.println("AllowOpFusion: " + OptimizerUtils.ALLOW_OPERATOR_FUSION);
		System.out.println("AllowSumProductRewrites: " + OptimizerUtils.ALLOW_SUM_PRODUCT_REWRITES);
		System.out.println("AllowConstantFolding: " + OptimizerUtils.ALLOW_CONSTANT_FOLDING);

		createRules((stmt, ctx) -> {
			testDMLStmt(stmt, ctx);
			return true;
		});

		for (Tuple4<RewriterStatement, List<Hop>, RewriterStatement, List<Hop>> incTransforms : costIncreasingTransformations) {
			System.out.println("==========");
			System.out.println(incTransforms._1());
			System.out.println("=>");
			System.out.println(incTransforms._3());
			System.out.println(countHops(incTransforms._2()) + " => " + countHops(incTransforms._4()));
		}
	}

	private void testDMLStmt(RewriterStatement stmt, final RuleContext ctx) {
		try {
			DMLScript.programInterceptor = interceptor;
			//System.setOut(new PrintStream(new CustomOutputStream(System.out, line -> System.err.println("INTERCEPT: " + line))));



			/*StringBuilder sb = new StringBuilder();
			sb.append(createVar("A", DataType.MATRIX, "random", Map.of("rows", 1, "cols", 10)));
			sb.append(createVar("B", DataType.MATRIX, "random", Map.of("rows", 1, "cols", 10)));
			sb.append("if(max(A) > 1) {print(lineage(A))} else {print(lineage(B))}\n");*/

			long timeMillis = System.currentTimeMillis();

			for (int i = 0; i < 1; i++) {
				String str = "print(" + stmt.toString(ctx) + ")";
				System.out.println("Executing:\n" + str);
				DMLScript.executeScript(new String[]{"-s", str});
			}

			System.err.println("Done in " + (System.currentTimeMillis() - timeMillis) + "ms");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private String createVar(String name, DataType dType, String genType, Map<String, Object> meta) {
		switch (dType) {
			case INT:
				throw new NotImplementedException();
			case FLOAT:
				throw new NotImplementedException();
			case MATRIX:
				return name + "=" + createMatrixVar(genType, meta) + "\n";
			default:
				throw new IllegalArgumentException("Unknown data type");
		}
	}

	private String createMatrixVar(String genType, Map<String, Object> meta) {
		switch (genType) {
			case "random":
				return createRandomMatrix((Integer)meta.get("rows"), (Integer)meta.get("cols"));
			default:
				throw new IllegalArgumentException("Unknown matrix generation type");
		}
	}

	private String createRandomMatrix(int nrows, int ncols) {
		//return String.format("matrix(1,rows=%d,cols=%d)", nrows, ncols);
		return String.format("RAND(rows=%d,cols=%d)", nrows, ncols);
	}








	private RewriterRuleSet createRules(BiFunction<RewriterStatement, RuleContext, Boolean> handler) {
		RuleContext ctx = RewriterContextSettings.getDefaultContext(new Random());

		ArrayList<RewriterRule> rules = new ArrayList<>();

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("LITERAL_BOOL:TRUE")
				.parseGlobalVars("LITERAL_INT:1")
				.withParsedStatement("TRUE")
				.toParsedStatement("<(_lower(1), 1)")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("INT:a")
				.parseGlobalVars("LITERAL_INT:1,0")
				.parseGlobalStatementAsVariable("LOWER", "_lower(a)")
				.withParsedStatement("LOWER")
				.toParsedStatement("as.scalar(rand(1, 1, +(LOWER, _lower(0)), LOWER))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("INT:a")
				.parseGlobalVars("LITERAL_INT:1,0")
				.parseGlobalStatementAsVariable("LOWER", "_lower(a)")
				.parseGlobalStatementAsVariable("p1", "_posInt()")
				.parseGlobalStatementAsVariable("p2", "_posInt()")
				.withParsedStatement("LOWER")
				.toParsedStatement("mean(rand(p1, p2, +(LOWER, _lower(0)), LOWER))")
				.build()
		);

		/*rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:a,b")
				.parseGlobalVars("FLOAT:c,d")
				.parseGlobalVars("LITERAL_INT:1,0")
				.parseGlobalStatementAsVariable("LOWER", "_lower(a)")
				.withParsedStatement("<(_opt[min,max,mean](rand(a, b, c, d)), d)")
				.toParsedStatement("as.scalar(rand(1, 1, +(LOWER, _lower(0)), LOWER))")
				.build()
		);*/

		Random rd = new Random();
		RewriterRuleSet ruleSet = new RewriterRuleSet(ctx, rules);
		RewriterDatabase db = new RewriterDatabase();

		String matrixDef = "MATRIX:A,B,C";
		String intDef = "LITERAL_BOOL:TRUE";
		String startStr = "TRUE";
		RewriterStatement stmt = RewriterUtils.parse(startStr, ctx, matrixDef, intDef);
		handler.apply(RewriterUtils.parse("+(2, 2)", ctx, "LITERAL_INT:2"), ctx);
		db.insertEntry(ctx, stmt);

		//RewriterRuleSet.ApplicableRule match = ruleSet.findFirstApplicableRule(stmt);
		ArrayList<RewriterRuleSet.ApplicableRule> applicableRules = ruleSet.findApplicableRules(stmt);

		RewriterStatement orig = stmt;
		RewriterStatement newStmt = stmt;

		for (int i = 0; i < 5 && !applicableRules.isEmpty(); i++) {
			int ruleIndex = rd.nextInt(applicableRules.size());
			RewriterRuleSet.ApplicableRule next = applicableRules.get(ruleIndex);

			if (next.forward)
				newStmt = next.rule.applyForward(next.matches.get(rd.nextInt(next.matches.size())), stmt, false);
			else
				newStmt = next.rule.applyBackward(next.matches.get(rd.nextInt(next.matches.size())), stmt, false);

			if (db.insertEntry(ctx, newStmt)) {
				stmt = newStmt;
				lastStatement = nextStatement;
				lastHops = nextHops;
				nextStatement = newStmt;

				if (!handler.apply(stmt, ctx))
					return ruleSet;

				applicableRules = ruleSet.findApplicableRules(stmt);
			} else {
				System.out.println("Duplicate entry found: " + newStmt.toString(ctx));
				System.out.println("Rule: " + next.rule);
				applicableRules.remove(ruleIndex);
			}
		}


		/*System.out.println(stmt.toString(ctx));
		System.out.println(next.toString(ctx));
		System.out.println(next.rule.applyForward(next.matches.get(0), stmt, true));*/

		return ruleSet;
	}





	private class CustomOutputStream extends OutputStream {
		private PrintStream ps;
		private StringBuilder buffer = new StringBuilder();
		private Consumer<String> lineHandler;

		public CustomOutputStream(PrintStream actualPrintStream, Consumer<String> lineHandler) {
			this.ps = actualPrintStream;
			this.lineHandler = lineHandler;
		}

		@Override
		public void write(int b) {
			char c = (char) b;
			if (c == '\n') {
				lineHandler.accept(buffer.toString());
				buffer.setLength(0); // Clear the buffer after handling the line
			} else {
				buffer.append(c); // Accumulate characters until newline
			}
			// Handle the byte 'b', or you can write to any custom destination
			ps.print((char) b); // Example: redirect to System.err
		}

		@Override
		public void write(byte[] b, int off, int len) {
			for (int i = off; i < off + len; i++) {
				write(b[i]);
			}
		}
	}
}
