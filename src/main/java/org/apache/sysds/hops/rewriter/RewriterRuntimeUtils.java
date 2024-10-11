package org.apache.sysds.hops.rewriter;

import org.apache.sysds.api.DMLScript;
import org.apache.sysds.common.Types;
import org.apache.sysds.hops.AggBinaryOp;
import org.apache.sysds.hops.AggUnaryOp;
import org.apache.sysds.hops.BinaryOp;
import org.apache.sysds.hops.DataGenOp;
import org.apache.sysds.hops.Hop;
import org.apache.sysds.hops.LiteralOp;
import org.apache.sysds.hops.ReorgOp;
import org.apache.sysds.parser.DMLProgram;
import org.apache.sysds.parser.StatementBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class RewriterRuntimeUtils {
	private static final String matrixDefs = "MATRIX:A,B,C";
	private static final String floatDefs = "FLOAT:q,r,s,t,f1,f2,f3,f4,f5";
	private static final String intDefs = "INT:i1,i2,i3,i4,i5";
	private static final String boolDefs = "BOOL:b1,b2,b3";

	public static void attachHopInterceptor(Function<DMLProgram, Boolean> interceptor) {
		DMLScript.hopInterceptor = interceptor;
	}

	// TODO: Make more flexible regarding program structure
	public static void forAllHops(DMLProgram program, Consumer<Hop> consumer) {
		for (StatementBlock sb : program.getStatementBlocks())
			sb.getHops().forEach(consumer);
	}

	public static RewriterStatement buildDAGFromHop(Hop hop, final RuleContext ctx) {
		return buildDAGRecursively(hop, new HashMap<>(), ctx);
	}

	private static RewriterStatement buildDAGRecursively(Hop next, Map<Hop, RewriterStatement> cache, final RuleContext ctx) {
		if (cache.containsKey(next))
			return cache.get(next);

		if (next instanceof LiteralOp) {
			RewriterStatement literal = buildLiteral((LiteralOp)next, ctx);
			cache.put(next, literal);
			return literal;
		}

		if (next instanceof AggBinaryOp) {
			RewriterStatement stmt = buildAggBinaryOp((AggBinaryOp) next, ctx);

			if (stmt == null)
				return null;

			if (buildInputs(stmt, next.getInput(), cache, true, ctx))
				return stmt;

			return null;
		}

		if (next instanceof AggUnaryOp) {
			RewriterStatement stmt = buildAggUnaryOp((AggUnaryOp) next, ctx);

			if (stmt == null)
				return null;

			if (buildInputs(stmt, next.getInput(), cache, true, ctx))
				return stmt;

			return null;
		}

		if (next instanceof BinaryOp) {
			RewriterStatement stmt = buildBinaryOp((BinaryOp) next, ctx);

			if (stmt == null)
				return null;

			if (buildInputs(stmt, next.getInput(), cache, true, ctx))
				return stmt;

			return null;
		}

		if (next instanceof ReorgOp) {
			RewriterStatement stmt = buildReorgOp((ReorgOp) next, ctx);

			if (stmt == null)
				return null;

			if (buildInputs(stmt, next.getInput(), cache, true, ctx))
				return stmt;

			return null;
		}

		if (next instanceof DataGenOp) {
			List<Hop> interestingHops = new ArrayList<>();
			RewriterStatement stmt = buildDataGenOp((DataGenOp)next, ctx, interestingHops);

			if (stmt == null)
				return null;

			if (buildInputs(stmt, interestingHops, cache, true, ctx))
				return stmt;

			return null;
		}

		System.out.println("Unknown Op: " + next);
		System.out.println("Class: " + next.getClass().getSimpleName());
		System.out.println("OPString: " + next.getOpString());

		return null;
	}

	private static boolean buildInputs(RewriterStatement stmt, List<Hop> inputs, Map<Hop, RewriterStatement> cache, boolean fixedSize, final RuleContext ctx) {
		List<RewriterStatement> children = new ArrayList<>();
		for (Hop in : inputs) {
			RewriterStatement childStmt = buildDAGRecursively(in, cache, ctx);

			if (childStmt == null) {
				System.out.println("Could not build child: " + in);
				return false;
			}

			children.add(childStmt);
		}

		if (fixedSize && stmt.getOperands().size() != children.size())
			return false;

		stmt.getOperands().clear();
		stmt.getOperands().addAll(children);
		stmt.consolidate(ctx);
		return true;
	}

	private static RewriterStatement buildAggBinaryOp(AggBinaryOp op, final RuleContext ctx) {
		// Some placeholder definitions
		switch(op.getOpString()) {
			case "ba(+*)": // Matrix multiplication
				return RewriterUtils.parse("%*%(A, B)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
		}

		System.out.println("Unknown AggBinaryOp: " + op.getOpString());
		return null;
	}

	private static RewriterStatement buildAggUnaryOp(AggUnaryOp op, final RuleContext ctx) {
		// Some placeholder definitions
		switch(op.getOpString()) {
			case "ua(+C)": // Matrix multiplication
				return RewriterUtils.parse("colSums(A)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
			case "ua(+R)":
				return RewriterUtils.parse("rowSums(A)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
			case "ua(+RC)":
				return RewriterUtils.parse("sum(A)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
		}

		System.out.println("Unknown AggUnaryOp: " + op.getOpString());
		return null;
	}

	private static RewriterStatement buildBinaryOp(BinaryOp op, final RuleContext ctx) {
		switch(op.getOpString()) {
			case "b(*)": // Matrix multiplication
				return RewriterUtils.parse("*(A, B)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
		}

		System.out.println("Unknown BinaryOp: " + op.getOpString());
		return null;
	}

	private static RewriterStatement buildReorgOp(ReorgOp op, final RuleContext ctx) {
		switch(op.getOpString()) {
			case "r(r')": // Matrix multiplication
				return RewriterUtils.parse("t(A)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
		}

		System.out.println("Unknown BinaryOp: " + op.getOpString());
		return null;
	}

	private static RewriterStatement buildDataGenOp(DataGenOp op, final RuleContext ctx, List<Hop> interestingHops) {
		// TODO:
		switch(op.getOpString()) {
			case "dg(rand)":
				interestingHops.add(op.getParam("rows"));
				interestingHops.add(op.getParam("cols"));
				interestingHops.add(op.getParam("min"));
				interestingHops.add(op.getParam("max"));
				return RewriterUtils.parse("rand(i1, i2, f1, f2)", ctx, matrixDefs, floatDefs, intDefs, boolDefs);
		}
		return null;
	}

	private static RewriterStatement buildLiteral(LiteralOp literal, final RuleContext ctx) {
		if (literal.getDataType() != Types.DataType.SCALAR)
			return null; // Then it is not supported yet

		switch (literal.getValueType()) {
			case FP64:
			case FP32:
				return new RewriterDataType().as(UUID.randomUUID().toString()).ofType("FLOAT").asLiteral(literal.getDoubleValue()).consolidate(ctx);
			case INT32:
			case INT64:
				return new RewriterDataType().as(UUID.randomUUID().toString()).ofType("INT").asLiteral(literal.getLongValue()).consolidate(ctx);
			case BOOLEAN:
				return new RewriterDataType().as(UUID.randomUUID().toString()).ofType("BOOL").asLiteral(literal.getBooleanValue()).consolidate(ctx);
			default:
				return null; // Not supported yet
		}
	}

	public static boolean executeScript(String script) {
		try {
			return DMLScript.executeScript(new String[]{"-s", script});
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}
}
