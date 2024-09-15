package org.apache.sysds.hops.rewriter;

import java.util.HashSet;

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
					.instrMeta("trueName", "+")
					.addOp("H")
						.ofType("MATRIX")
					.addOp("K")
						.ofType("MATRIX")
					.as("H + K")
				.withInstruction("rowSelect")
					.addExistingOp("H + K")
					.addOp("n")
						.ofType("INT")
					.addOp("m")
						.ofType("INT")
					.as("rowSelect(H + K, n, m)")
				.withInstruction("rowSelect")
					.addExistingOp("rowSelect(H + K, n, m)")
					.addOp("a")
						.ofType("INT")
					.addOp("b")
						.ofType("INT")
					.asRootInstruction()
				.buildDAG();
	}

	public static RewriterInstruction selectionPushdownExample3() {
		HashSet<String> mProperties = new HashSet<>();
		mProperties.add("RowSelectPushableBinaryInstruction");
		return (RewriterInstruction)new RewriterRuleBuilder(RuleContext.selectionPushdownContext)
				.asDAGBuilder()
				.withInstruction("+") // This is more a class of instructions
				.instrMeta("trueName", "+")
				.addOp("H")
				.ofType("MATRIX")
				.addOp("K")
				.ofType("MATRIX")
				.as("H + K")
				.withInstruction("rowSelect")
				.instrMeta("trueName", "[,?]")
				.addExistingOp("H + K")
				.addOp("n")
				.ofType("INT")
				.addOp("m")
				.ofType("INT")
				.as("rowSelect(H + K, n, m)")
				.withInstruction("rowSelect")
				.addExistingOp("rowSelect(H + K, n, m)")
				.addOp("a")
				.ofType("INT")
				.addOp("b")
				.ofType("INT")
				.asRootInstruction()
				.buildDAG();
	}
}
