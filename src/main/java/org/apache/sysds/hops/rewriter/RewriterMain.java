package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RewriterMain {

	public static void main(String[] args) {
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

		System.out.println(rule1);

		RewriterRule ruleMatch = new RewriterRuleBuilder()
				.withInstruction("+")
					.addOp("c")
						.ofType("float")
					.addOp("d")
						.ofType("float")
					.as("c+d")
				.withInstruction("+")
					.addOp("e")
						.ofType("float")
					.addExistingOp("c+d")
					.asRootInstruction()
				.toInstruction("+")
					.addExistingOp("d")
					.addExistingOp("c")
					.as("d+c")
					.asRootInstruction()
				.build();

		ArrayList<RewriterStatement.MatchingSubexpression> matches = new ArrayList<>();
		if (rule1.getStmt1().matchSubexpr(ruleMatch.getStmt1(), matches, new HashMap<>())) {
			System.out.println("Matches detected!");
			int ctr = 1;
			for (RewriterStatement.MatchingSubexpression match : matches) {
				System.out.println("Match " + ctr++ + ": ");
				System.out.println(" " + match.getMatchRoot() + " = " + rule1.getStmt1());
				System.out.println();
				for (Map.Entry<RewriterDataType, RewriterStatement> entry : match.getAssocs().entrySet()) {
					System.out.println(" - " + entry.getValue() + "::" + entry.getValue().getResultingDataType() + " -> " + entry.getKey().getId() + "::" + entry.getKey().getResultingDataType());
				}
				System.out.println();
			}
		}

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

		System.out.println(rule2);
	}
}
