package org.apache.sysds.hops.rewriter;

public class RewriterMain {

	public static void main(String[] args) {
		System.out.println("Hello there!");
		RewriterRule rule1 = new RewriterRuleBuilder()
				.withInstruction("+")
					.addOp("a")
						.ofType("float")
					.addOp("b")
						.ofType("float")
					.as("a+b")
					.asRootInstruction()
				.toInstruction("+")
					.addExistingOp("b")
					.addExistingOp("a")
					.as("b+a")
					.asRootInstruction()
				.build();

		RewriterRule rule2 = new RewriterRuleBuilder()
				.withInstruction("+")
					.addOp("a")
						.ofType("float")
					.addOp("b")
						.ofType("float")
					.as("a+b")
				.withInstruction("*")
					.addExistingOp("a+b")
					.addOp("c")
						.ofType("float")
					.asRootInstruction()
				.toInstruction("*")
					.addExistingOp("a")
					.addExistingOp("c")
					.as("a*c")
				.toInstruction("*")
					.addExistingOp("b")
					.addExistingOp("c")
					.as("b*c")
				.toInstruction("+")
					.addExistingOp("a*c")
					.addExistingOp("b*c")
					.asRootInstruction()
				.build();
	}
}
