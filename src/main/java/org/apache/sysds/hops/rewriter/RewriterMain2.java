package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class RewriterMain2 {

	public static void main(String[] args) {
		StringBuilder builder = new StringBuilder();

		builder.append("IdxSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl /\n");
		builder.append("impl min\n");
		builder.append("impl max\n");

		builder.append("RowSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");

		builder.append("ColSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");

		builder.append("RowSelectMMPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl %*%\n");

		builder.append("ColSelectMMPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl %*%\n");

		builder.append("rowSelect(MATRIX,INT,INT)::MATRIX\n");
		builder.append("colSelect(MATRIX,INT,INT)::MATRIX\n");
		builder.append("min(INT,INT)::INT\n");
		builder.append("max(INT,INT)::INT\n");

		builder.append("index(MATRIX,INT,INT,INT,INT)::MATRIX\n");

		builder.append("FusableBinaryOperator(MATRIX, MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl %*%\n");

		builder.append("FusedOperator(MATRIX...)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl %*%\n");

		RuleContext ctx = RuleContext.createContext(builder.toString());
		ctx.customStringRepr.put("+(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" + "));
		ctx.customStringRepr.put("-(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" - "));
		ctx.customStringRepr.put("*(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" * "));
		ctx.customStringRepr.put("/(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" / "));
		ctx.customStringRepr.put("index(MATRIX,INT,INT,INT,INT)", stmt -> {
			String out;
			RewriterInstruction mInstr = (RewriterInstruction) stmt;
			List<RewriterStatement> ops = mInstr.getOperands();
			RewriterStatement op1 = ops.get(0);

			if (op1 instanceof RewriterDataType)
				out = op1.toString();
			else
				out = "(" + op1 + ")";

			out += "[" + ops.get(1) + " : " + ops.get(2) + ", " + ops.get(3) + " : " + ops.get(4) + "]";
			return out;
		});

		System.out.println(ctx.instrTypes);
		System.out.println(ctx.instrProperties);

		//RewriterRuleSet ruleSet = RewriterRuleSet.selectionPushdown;

		RewriterInstruction instr = RewriterExamples.selectionPushdownExample4(ctx);

		RewriterHeuristic selectionBreakup = new RewriterHeuristic(RewriterRuleSet.buildSelectionBreakup(ctx), List.of("index"));
		RewriterHeuristic selectionPushdown = new RewriterHeuristic(RewriterRuleSet.buildSelectionPushdownRuleSet(ctx), List.of("IdxSelectPushableBinaryInstruction(MATRIX,MATRIX)", "RowSelectPushableBinaryInstruction(MATRIX,MATRIX)", "ColSelectPushableBinaryInstruction(MATRIX,MATRIX)"));
		RewriterHeuristic selectionSimplification = new RewriterHeuristic(RewriterRuleSet.buildSelectionSimplification(ctx), List.of("IdxSelectPushableBinaryInstruction(MATRIX,MATRIX)", "RowSelectPushableBinaryInstruction(MATRIX,MATRIX)", "ColSelectPushableBinaryInstruction(MATRIX,MATRIX)"));
		RewriterHeuristic operatorFusion = new RewriterHeuristic(RewriterRuleSet.buildOperatorFusion(ctx), List.of("FusableBinaryOperator(MATRIX,MATRIX)", "FusedOperator(MATRIX...)"));

		long millis = System.currentTimeMillis();

		System.out.println();
		System.out.println("> SELECTION BREAKUP <");
		System.out.println();

		instr = selectionBreakup.apply(instr, current -> {
			System.out.println(current);
			return true;
		});

		System.out.println();
		System.out.println("> SELECTION PUSHDOWN <");
		System.out.println();

		instr = selectionPushdown.apply(instr, current -> {
			System.out.println(current);
			return true;
		});

		System.out.println();
		System.out.println("> SELECTION SIMPLIFICATION <");
		System.out.println();

		instr = selectionSimplification.apply(instr, current -> {
			System.out.println(current);
			return true;
		});

		System.out.println();
		System.out.println("> OPERATOR FUSION <");
		System.out.println();

		instr = operatorFusion.apply(instr, current -> {
			System.out.println(current);
			return true;
		});

		millis = System.currentTimeMillis() - millis;
		System.out.println("Finished in " + millis + "ms");
	}
}
