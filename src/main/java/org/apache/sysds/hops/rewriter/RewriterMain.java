package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.ArrayList;
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

		RewriterInstruction instr = new RewriterRuleBuilder()
				.asDAGBuilder()
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
				.buildDAG();

		ArrayList<RewriterStatement.MatchingSubexpression> matches = new ArrayList<>();
		if (rule1.getStmt1().matchSubexpr(instr, null, -1, matches, new DualHashBidiMap<>())) {
			System.out.println("Matches detected!");
			int ctr = 1;
			for (RewriterStatement.MatchingSubexpression match : matches) {
				System.out.println("Match " + ctr++ + ": ");
				System.out.println(" " + match.getMatchRoot() + " = " + rule1.getStmt1());
				System.out.println();
				for (Map.Entry<RewriterStatement, RewriterStatement> entry : match.getAssocs().entrySet()) {
					System.out.println(" - " + entry.getKey() + "::" + entry.getKey().getResultingDataType() + " -> " + entry.getValue().getId() + "::" + entry.getValue().getResultingDataType());
				}
				System.out.println();
			}

			System.out.println("Applying the first transformation rule: ");
			System.out.print(instr + " => ");
			instr = rule1.applyForward(matches.get(1), instr, false);
			System.out.println(instr);
			//System.out.println(instr.linksToString());

			matches.clear();
			ctr = 1;

			rule1.getStmt1().matchSubexpr(instr, null, -1, matches, new DualHashBidiMap<>());
			System.out.println("Number of matches: " + matches.size());
			for (RewriterStatement.MatchingSubexpression match : matches) {
				System.out.println("Match " + ctr++ + ": ");
				//System.out.println(" " + match.getMatchRoot().toStringWithLinking(instr.getLinks()) + " = " + rule1.getStmt1());
				System.out.println();
				for (Map.Entry<RewriterStatement, RewriterStatement> entry : match.getAssocs().entrySet()) {
					System.out.println(" - " + entry.getKey() + "::" + entry.getKey().getResultingDataType() + " -> " + entry.getValue().getId() + "::" + entry.getValue().getResultingDataType());
				}
				System.out.println();
			}
			System.out.println("Applying the second transformation rule: ");
			System.out.print(instr + " => ");
			//System.out.println(instr.linksToString());
			instr = rule1.applyForward(matches.get(0), instr, false);
			System.out.println(instr);
			/*System.out.println("Links:");
			for (Map.Entry<RewriterStatement, RewriterStatement> entry : instr.getLinks().entrySet()) {
				System.out.println(entry.getKey() + " -> " + entry.getValue());
			}*/
			//System.out.println(instr.linksToString());
			//System.out.println(instr);
		}

		/*RewriterRule rule2 = new RewriterRuleBuilder()
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

		System.out.println(rule2);*/
	}
}
