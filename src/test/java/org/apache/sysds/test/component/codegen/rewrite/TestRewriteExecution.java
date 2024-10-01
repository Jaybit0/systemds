package org.apache.sysds.test.component.codegen.rewrite;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.sysds.api.DMLScript;
import org.apache.sysds.hops.Hop;
import org.apache.sysds.hops.LiteralOp;
import org.apache.sysds.hops.OptimizerUtils;
import org.apache.sysds.hops.rewriter.RewriterContextSettings;
import org.apache.sysds.hops.rewriter.RewriterDatabase;
import org.apache.sysds.hops.rewriter.RewriterHeuristic;
import org.apache.sysds.hops.rewriter.RewriterRule;
import org.apache.sysds.hops.rewriter.RewriterRuleCollection;
import org.apache.sysds.hops.rewriter.RewriterRuleSet;
import org.apache.sysds.hops.rewriter.RewriterStatement;
import org.apache.sysds.hops.rewriter.RewriterUtils;
import org.apache.sysds.hops.rewriter.RuleContext;
import org.apache.sysds.parser.DMLProgram;
import org.apache.sysds.parser.StatementBlock;
import org.junit.Test;
import scala.Tuple6;

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
	private String lastProg = null;
	private RewriterStatement nextStatement = null;
	private List<Hop> nextHops = null;
	private String nextProg = null;
	private List<Tuple6<RewriterStatement, String, List<Hop>, RewriterStatement, String, List<Hop>>> costIncreasingTransformations = new ArrayList<>();

	private BiConsumer<DMLProgram, String> interceptor = (prog, phase) -> {
		if (!phase.equals("HOPRewrites"))
			return;
		//int hopCtr = 0;
		for (StatementBlock sb : prog.getStatementBlocks()) {
			int hopCount = sb.getHops() == null ? 0 : sb.getHops().stream().mapToInt(this::countHops).sum();
			System.out.println("HopCount: " + hopCount);
			nextHops = sb.getHops();

			if (lastStatement == null) {
				currentHopCount = hopCount;
			} else if (hopCount > currentHopCount) {
				costIncreasingTransformations.add(new Tuple6<>(lastStatement, lastProg, lastHops, nextStatement, nextProg, nextHops));
				currentHopCount = hopCount;
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

	private String toDMLString(RewriterStatement stmt, final RuleContext ctx) {
		List<String> execStr = stmt.toExecutableString(ctx);
		boolean isMatrix = stmt.getResultingDataType(ctx).equals("MATRIX");
		String resString;
		if (isMatrix)
			resString = "print(toString(" + execStr.get(execStr.size()-1) + "))";
		else
			resString = "print(" + execStr.get(execStr.size()-1) + ")";
		execStr.set(execStr.size()-1, resString);
		return String.join("\n", execStr);
	}

	@Test
	public void test() {
		System.out.println("OptLevel:" + OptimizerUtils.getOptLevel().toString());
		System.out.println("AllowOpFusion: " + OptimizerUtils.ALLOW_OPERATOR_FUSION);
		System.out.println("AllowSumProductRewrites: " + OptimizerUtils.ALLOW_SUM_PRODUCT_REWRITES);
		System.out.println("AllowConstantFolding: " + OptimizerUtils.ALLOW_CONSTANT_FOLDING);

		createRules((stmt, ctx) -> {
			try {
				testDMLStmt(stmt, ctx);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		});

		System.out.println("===== FOUND TRANSFORMATIONS =====");

		for (Tuple6<RewriterStatement, String, List<Hop>, RewriterStatement, String, List<Hop>> incTransforms : costIncreasingTransformations) {
			System.out.println("==========");
			System.out.println(incTransforms._2());
			System.out.println("=>");
			System.out.println(incTransforms._5());
			System.out.println(countHops(incTransforms._3()) + " => " + countHops(incTransforms._6()));
		}
	}

	private void testDMLStmt(RewriterStatement stmt, final RuleContext ctx) {
		try {
			RewriterHeuristic heur = RewriterRuleCollection.getHeur(ctx);
			stmt = heur.apply(stmt);

			DMLScript.programInterceptor = interceptor;
			//System.setOut(new PrintStream(new CustomOutputStream(System.out, line -> System.err.println("INTERCEPT: " + line))));



			/*StringBuilder sb = new StringBuilder();
			sb.append(createVar("A", DataType.MATRIX, "random", Map.of("rows", 1, "cols", 10)));
			sb.append(createVar("B", DataType.MATRIX, "random", Map.of("rows", 1, "cols", 10)));
			sb.append("if(max(A) > 1) {print(lineage(A))} else {print(lineage(B))}\n");*/

			long timeMillis = System.currentTimeMillis();

			String str = toDMLString(stmt, ctx);
			lastProg = nextProg;
			nextProg = str;
			System.out.println("Executing:\n" + str);
			DMLScript.executeScript(new String[]{"-s", str});

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

		RewriterRuleCollection.addEqualitySubstitutions(rules, ctx);
		RewriterRuleCollection.addBooleAxioms(rules, ctx);

		/*rules.add(new RewriterRuleBuilder(ctx)
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

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("mean(A)")
				.toParsedStatement("/(sum(A),*(ncol(A),nrow(A)))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("mean(A)")
				.toParsedStatement("/(sum(A),length(A))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("LITERAL_INT:1")
				.parseGlobalStatementAsVariable("DIFF", "-(A, mean(A))")
				.withParsedStatement("var(A)")
				.toParsedStatement("*(/(1, length(A)), sum(*(DIFF, DIFF)))")
				.build()
		);

		rules.add(new RewriterRuleBuilder(ctx)
				.parseGlobalVars("MATRIX:A")
				.withParsedStatement("length(A)")
				.toParsedStatement("*(ncol(A),nrow(A))")
				.build()
		);*/

		Random rd = new Random();
		RewriterRuleSet ruleSet = new RewriterRuleSet(ctx, rules);
		RewriterDatabase db = new RewriterDatabase();

		String matrixDef = "MATRIX:A,B,C";
		String intDef = "LITERAL_INT:10";
		String floatDef = "LITERAL_FLOAT:0,1,-0.0001,0.0001,-1";
		String boolDef = "LITERAL_BOOL:TRUE,FALSE";
		String startStr = "TRUE";
		//String startStr = "var(rand(10, 10, 0, 1))";
		//String startStr = "sum(!=(rand(10, 10, -0.0001, 0.0001), 0))";
		//String startStr = "<(*($1:rand(10, 10, -1, 1), $1), 0)";
		//String startStr = "rand(10, 10, $1:_rdFloat(), $1)";
		//String startStr = "sum(==(rand(10, 10, 0, 1), 1))";
		RewriterStatement stmt = RewriterUtils.parse(startStr, ctx, matrixDef, intDef, floatDef, boolDef);
		//handler.apply(RewriterUtils.parse("+(2, 2)", ctx, "LITERAL_INT:2"), ctx);
		db.insertEntry(ctx, stmt);

		//RewriterRuleSet.ApplicableRule match = ruleSet.findFirstApplicableRule(stmt);
		long millis = System.currentTimeMillis();
		ArrayList<RewriterRuleSet.ApplicableRule> applicableRules = ruleSet.findApplicableRules(stmt);

		RewriterStatement newStmt = stmt;

		nextStatement = stmt;
		if (!handler.apply(stmt, ctx))
			return ruleSet;

		for (int i = 0; i < 1000 && !applicableRules.isEmpty(); i++) {
			int ruleIndex = rd.nextInt(applicableRules.size());
			RewriterRuleSet.ApplicableRule next = applicableRules.get(ruleIndex);

			if (next.forward)
				newStmt = next.rule.applyForward(next.matches.get(rd.nextInt(next.matches.size())), stmt, false);
			else
				newStmt = next.rule.applyBackward(next.matches.get(rd.nextInt(next.matches.size())), stmt, false);

			System.out.println("Rewrite took " + (System.currentTimeMillis() - millis) + "ms");

			if (db.insertEntry(ctx, newStmt)) {
				stmt = newStmt;
				lastStatement = nextStatement;
				lastHops = nextHops;
				nextStatement = newStmt;

				if (!handler.apply(stmt, ctx))
					return ruleSet;

				millis = System.currentTimeMillis();

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
