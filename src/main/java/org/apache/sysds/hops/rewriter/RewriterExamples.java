package org.apache.sysds.hops.rewriter;

public class RewriterExamples {
	public static RewriterInstruction selectionPushdownExample1() {
		return (RewriterInstruction)new RewriterRuleBuilder(RuleContext.selectionPushdownContext)
				.asDAGBuilder()
				.withInstruction("RowSelectPushableBinaryInstruction") // This is more a class of instructions
					.addOp("A")
						.ofType("MATRIX")
					.addOp("B")
						.ofType("MATRIX")
					.as("A + B")
				.withInstruction("rowSelect")
					.addExistingOp("A + B")
					.addOp("i")
						.ofType("INT")
					.addOp("j")
						.ofType("INT")
					.asRootInstruction()
				.buildDAG();
	}

	public static RewriterInstruction selectionPushdownExample2() {
		return (RewriterInstruction)new RewriterRuleBuilder(RuleContext.selectionPushdownContext)
				.asDAGBuilder()
				.withInstruction("RowSelectPushableBinaryInstruction") // This is more a class of instructions
					.addOp("A")
						.ofType("MATRIX")
					.addOp("B")
						.ofType("MATRIX")
					.as("A + B")
				.withInstruction("rowSelect")
					.addExistingOp("A + B")
					.addOp("i")
						.ofType("INT")
					.addOp("j")
						.ofType("INT")
					.as("rowSelect(A + B, i, j)")
				.withInstruction("rowSelect")
					.addExistingOp("rowSelect(A + B, i, j)")
					.addOp("a")
						.ofType("INT")
					.addOp("b")
						.ofType("INT")
					.asRootInstruction()
				.buildDAG();
	}
}
