package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class RewriterMain2 {

	public static void main(String[] args) {
		StringBuilder builder = new StringBuilder();

		builder.append("argList(MATRIX)::MATRIX...\n"); // This is a meta function that can take any number of MATRIX arguments

		builder.append("IdxSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl /\n");
		builder.append("impl min\n");
		builder.append("impl max\n");

		builder.append("RowSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl IdxSelectPushableBinaryInstruction\n");
		builder.append("impl CBind\n");

		builder.append("ColSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl IdxSelectPushableBinaryInstruction\n");
		builder.append("impl RBind\n");

		builder.append("IdxSelectMMPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl %*%\n");

		builder.append("RowSelectMMPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl IdxSelectMMPushableBinaryInstruction\n");

		builder.append("ColSelectMMPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl IdxSelectMMPushableBinaryInstruction\n");

		builder.append("IdxSelectTransposePushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl t\n");

		builder.append("RowSelectTransposePushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl IdxSelectTransposePushableBinaryInstruction\n");

		builder.append("ColSelectTransposePushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl IdxSelectTransposePushableBinaryInstruction\n");

		// Aggregation functions

		builder.append("FullAggregationInstruction(MATRIX)::FLOAT\n");
		builder.append("impl mean\n");

		builder.append("RowAggregationInstruction(MATRIX)::MATRIX\n");
		builder.append("impl rowSums\n");

		builder.append("ColAggregationInstruction(MATRIX)::MATRIX\n");
		builder.append("impl colSums\n");



		// Function aggregation properties

		builder.append("FullAggregationPushableInstruction(FLOAT,FLOAT)::FLOAT\n");
		builder.append("impl +\n");
		builder.append("impl -\n");

		builder.append("FullAggregationPushableInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");

		builder.append("RowAggregationPushableInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl FullAggregationPushableInstruction\n");
		builder.append("impl rowSums\n");

		builder.append("ColAggregationPushableInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl FullAggregationPushableInstruction\n");
		builder.append("impl colSums\n");


		// Permutation functions

		builder.append("Rearrangement(MATRIX)::MATRIX\n"); // An operation that keeps all elements but can reformat the matrix
		builder.append("impl Permutation\n");
		builder.append("impl t\n"); // Transposition

		builder.append("RowPermutation(MATRIX)::MATRIX\n");

		builder.append("ColPermutation(MATRIX)::MATRIX\n");

		builder.append("Permutation(MATRIX)::MATRIX\n");
		builder.append("impl RowPermutation\n");
		builder.append("impl ColPermutation\n");
		//builder.append("impl t\n"); // Transpose matrix



		// Matrix extending operations

		builder.append("CBind(MATRIX,MATRIX)::MATRIX\n");
		builder.append("RBind(MATRIX,MATRIX)::MATRIX\n");


		// Meta preserving instructions

		builder.append("SizePreservingInstruction(MATRIX,MATRIX)::MATRIX\n"); // Maintains the size information of the matrix
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl /\n");
		builder.append("impl min\n");
		builder.append("impl max\n");

		builder.append("SizeSwappingInstruction(MATRIX)::MATRIX\n");
		builder.append("impl t\n");

		builder.append("SizeInstruction(MATRIX)::INT\n");
		builder.append("impl nrows\n");
		builder.append("impl ncols\n");



		// Element-wise instruction
		builder.append("ElementWiseInstruction(MATRIX, MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl /\n");
		builder.append("impl max\n");
		builder.append("impl min\n");

		builder.append("rowSelect(MATRIX,INT,INT)::MATRIX\n");
		builder.append("colSelect(MATRIX,INT,INT)::MATRIX\n");
		builder.append("min(INT,INT)::INT\n");
		builder.append("max(INT,INT)::INT\n");

		builder.append("index(MATRIX,INT,INT,INT,INT)::MATRIX\n");

		builder.append("FusableBinaryOperator(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl %*%\n");

		builder.append("FusedOperator(MATRIX...)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl *\n");
		builder.append("impl %*%\n");

		builder.append("ncols(MATRIX)::INT\n");
		builder.append("nrows(MATRIX)::INT\n");

		builder.append("-(INT,INT)::INT\n");
		builder.append("+(INT,INT)::INT\n");

		builder.append("-(FLOAT,FLOAT)::FLOAT\n");
		builder.append("+(FLOAT,FLOAT)::FLOAT\n");

		builder.append("-(INT,FLOAT)::FLOAT\n");
		builder.append("+(INT,FLOAT)::FLOAT\n");

		builder.append("-(FLOAT,INT)::FLOAT\n");
		builder.append("+(FLOAT,INT)::FLOAT\n");

		// Some bool algebra
		builder.append("<=(INT,INT)::INT\n");
		builder.append("==(INT,INT)::INT\n");
		builder.append("&&(INT,INT)::INT\n");

		builder.append("if(INT,MATRIX,MATRIX)::MATRIX\n");

		// Compile time functions
		builder.append("_compileTimeIsEqual(MATRIX,MATRIX)::INT\n");
		builder.append("_compileTimeSelectLeastExpensive(MATRIX,MATRIX)::MATRIX\n"); // Selects the least expensive of the two matrices to obtain

		RuleContext ctx = RuleContext.createContext(builder.toString());
		ctx.customStringRepr.put("+(INT,INT)", RewriterUtils.binaryStringRepr(" + "));
		ctx.customStringRepr.put("+(FLOAT,FLOAT)", RewriterUtils.binaryStringRepr(" + "));
		ctx.customStringRepr.put("+(INT,FLOAT)", RewriterUtils.binaryStringRepr(" + "));
		ctx.customStringRepr.put("+(FLOAT,INT)", RewriterUtils.binaryStringRepr(" + "));
		ctx.customStringRepr.put("-(INT,INT)", RewriterUtils.binaryStringRepr(" - "));
		ctx.customStringRepr.put("-(FLOAT,INT)", RewriterUtils.binaryStringRepr(" - "));
		ctx.customStringRepr.put("-(INT,FLOAT)", RewriterUtils.binaryStringRepr(" - "));
		ctx.customStringRepr.put("-(FLOAT,FLOAT)", RewriterUtils.binaryStringRepr(" - "));


		ctx.customStringRepr.put("+(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" + "));
		ctx.customStringRepr.put("-(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" - "));
		ctx.customStringRepr.put("*(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" * "));
		ctx.customStringRepr.put("/(MATRIX,MATRIX)", RewriterUtils.binaryStringRepr(" / "));
		ctx.customStringRepr.put("<=(INT,INT)", RewriterUtils.binaryStringRepr(" <= "));
		ctx.customStringRepr.put("==(INT,INT)", RewriterUtils.binaryStringRepr(" == "));
		ctx.customStringRepr.put("&&(INT,INT)", RewriterUtils.binaryStringRepr(" && "));
		ctx.customStringRepr.put("index(MATRIX,INT,INT,INT,INT)", (stmt, ctx2) -> {
			String out;
			RewriterInstruction mInstr = (RewriterInstruction) stmt;
			List<RewriterStatement> ops = mInstr.getOperands();
			RewriterStatement op1 = ops.get(0);

			if (op1 instanceof RewriterDataType)
				out = op1.toString(ctx2);
			else
				out = "(" + op1.toString(ctx2) + ")";

			out += "[" + ops.get(1).toString(ctx2) + " : " + ops.get(2).toString(ctx2) + ", " + ops.get(3).toString(ctx2) + " : " + ops.get(4).toString(ctx2) + "]";
			return out;
		});
		ctx.customStringRepr.put("argList(MATRIX)", (stmt, ctx2) -> {
			RewriterInstruction mInstr = (RewriterInstruction) stmt;
			String out = mInstr.getOperands().get(0).toString(ctx2);

			for (int i = 1; i < mInstr.getOperands().size(); i++)
				out += ", " + mInstr.getOperands().get(i).toString(ctx2);

			return out;
		});
		ctx.customStringRepr.put("if(INT,MATRIX,MATRIX)", (stmt, ctx2) -> {
			RewriterInstruction mInstr = (RewriterInstruction) stmt;
			StringBuilder sb = new StringBuilder();
			sb.append("if (");
			sb.append(mInstr.getOperands().get(0));
			sb.append(")\n");
			sb.append("{\n");
			sb.append(mInstr.getOperands().get(1));
			sb.append("\n}\nelse\n{\n");
			sb.append(mInstr.getOperands().get(2));
			sb.append("\n}");
			return sb.toString();
		});

		/*HashMap<Integer, RewriterStatement> mHooks = new HashMap<>();
		RewriterRule rule = new RewriterRuleBuilder(ctx)
				.parseGlobalVars("MATRIX:A")
				.parseGlobalVars("INT:h,i,j,k")
				.withParsedStatement("index(A,h,i,j,k)", mHooks)
				.toParsedStatement("rowSelect(colSelect(A,j,k),h,i)", mHooks)
				.build();
		System.out.println(rule);
		if (true)
			return;

		HashMap<String, RewriterStatement> vars = new HashMap<>();
		HashMap<Integer, RewriterStatement> hooks = new HashMap<>();
		RewriterUtils.parseDataTypes("INT:test", vars, ctx);

		RewriterStatement stmt = RewriterUtils.parseExpression(new MutableObject<>("$2:+(test,$1:test2())"), hooks, vars, ctx);
		System.out.println(hooks);
		System.out.println(stmt.toString(ctx));*/

		System.out.println(ctx.instrTypes);
		System.out.println(ctx.instrProperties);

		//RewriterRuleSet ruleSet = RewriterRuleSet.selectionPushdown;

		// Assumptions: Semantic correctness (e.g. CBind(A, B), A, B already have the same number of rows)

		// TODO: Adapt matcher such that for instance RBind(A, A) matches RBind(A, B); BUT: Not the other way round

		RewriterHeuristic selectionBreakup = new RewriterHeuristic(RewriterRuleSet.buildSelectionBreakup(ctx));

		RewriterHeuristic selectionPushdown = new RewriterHeuristic(RewriterRuleSet.buildSelectionPushdownRuleSet(ctx));
		RewriterHeuristic rbindcbindPushdown = new RewriterHeuristic(RewriterRuleSet.buildRbindCbindSelectionPushdown(ctx));

		RewriterHeuristic rbindElimination = new RewriterHeuristic(RewriterRuleSet.buildRBindElimination(ctx));

		RewriterHeuristic prepareCBindElimination = new RewriterHeuristic(RewriterRuleSet.buildReorderColRowSelect("colSelect", "rowSelect", ctx));
		RewriterHeuristic cbindElimination = new RewriterHeuristic(RewriterRuleSet.buildCBindElimination(ctx));

		RewriterHeuristic prepareSelectionSimplification = new RewriterHeuristic(RewriterRuleSet.buildReorderColRowSelect("rowSelect", "colSelect", ctx));
		RewriterHeuristic selectionSimplification = new RewriterHeuristic(RewriterRuleSet.buildSelectionSimplification(ctx));

		// TODO: This is still narrow and experimental
		RewriterHeuristic elementWiseInstructionPushdown = new RewriterHeuristic(RewriterRuleSet.buildElementWiseInstructionPushdown(ctx));

		// TODO: Eliminate e.g. colSums(colSums(A))

		RewriterHeuristic aggregationPushdown = new RewriterHeuristic(RewriterRuleSet.buildAggregationPushdown(ctx));

		RewriterHeuristic metaInstructionSimplification = new RewriterHeuristic(RewriterRuleSet.buildMetaInstructionSimplification(ctx));

		RewriterHeuristic operatorFusion = new RewriterHeuristic(RewriterRuleSet.buildDynamicOpInstructions(ctx));

		//System.out.println(RewriterRuleSet.buildRbindCbindSelectionPushdown(ctx));

		//for (int i = 0; i < 100; i++) {
			//RewriterInstruction instr = RewriterExamples.selectionPushdownExample4(ctx);
		String matrixDef = "MATRIX:A,B,C";
		String intDef = "INT:q,r,s,t,i,j,k,l";
		//String expr = "colSelect(CBind(index(A, q, r, s, t), B), a, b)";
		//String expr = "RBind(CBind(index(A,q,r,s,t), index(A,i,j,k,l)), A)";
		String expr = "colSelect(RBind(index(CBind(colSums(-(t(rowSums(t(+(A,B)))), t(C))), rowSelect(C, q, r)), q, r, s, t), rowSelect(B, k, l)), i, j)";
		//String expr = "mean(RowPermutation(A))";
		//String expr = "rowSums(+(A,B))";
		RewriterInstruction instr = (RewriterInstruction) RewriterUtils.parse(expr, ctx, matrixDef, intDef);

		long millis = System.currentTimeMillis();

		System.out.println();
		System.out.println("> SELECTION BREAKUP <");
		System.out.println();

		instr = selectionBreakup.apply(instr, current -> {
			System.out.println(current);
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> SELECTION PUSHDOWN <");
		System.out.println();

		MutableBoolean foundRewrites = new MutableBoolean(true);

		while (foundRewrites.booleanValue()) {
			foundRewrites.setValue(false);

			instr = selectionPushdown.apply(instr, current -> {
				System.out.println(current);
				System.out.println("<<<");
				System.out.println();
				return true;
			}, foundRewrites);

			instr = rbindcbindPushdown.apply(instr, current -> {
				System.out.println(current);
				System.out.println("<<<");
				System.out.println();
				return true;
			}, foundRewrites);
		}

		System.out.println();
		System.out.println("> DYNAMIC RBIND/CBIND ELIMINATION <");
		System.out.println();

		instr = rbindElimination.apply(instr, current -> {
			System.out.println(current);
			System.out.println();
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		instr = prepareCBindElimination.apply(instr);

		instr = cbindElimination.apply(instr, current -> {
			System.out.println(current);
			System.out.println();
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> SELECTION SIMPLIFICATION <");
		System.out.println();

		instr = prepareSelectionSimplification.apply(instr);

		instr = selectionSimplification.apply(instr, current -> {
			System.out.println(current);
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> ELEMENT-WISE INSTRUCTION PUSHDOWN <");
		System.out.println();

		instr = elementWiseInstructionPushdown.apply(instr, current -> {
			System.out.println(current);
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> AGGREGATION PUSHDOWN <");
		System.out.println();

		instr = aggregationPushdown.apply(instr, current -> {
			System.out.println(current);
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> META INSTRUCTION SIMPLIFICATION <");
		System.out.println();

		instr = metaInstructionSimplification.apply(instr, current -> {
			System.out.println(current);
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> OPERATOR FUSION <");
		System.out.println();

		instr = operatorFusion.apply(instr, current -> {
			System.out.println(current);
			System.out.println("<<<");
			System.out.println();
			return true;
		});

		System.out.println();
		System.out.println("> OPERATOR MERGE <");
		System.out.println();

		RewriterUtils.mergeArgLists(instr, ctx);
		System.out.println(instr);
		/*instr = operatorMerge.apply(instr, current -> {
			System.out.println(current);
			return true;
		});*/

		millis = System.currentTimeMillis() - millis;
		System.out.println("Finished in " + millis + "ms");
		//}

	}
}
