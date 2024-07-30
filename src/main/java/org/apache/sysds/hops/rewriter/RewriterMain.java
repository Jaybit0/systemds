package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Collectors;

public class RewriterMain {

	private static RewriterRuleSet ruleSet;

	static {
		RewriterRule ruleAddCommut = new RewriterRuleBuilder()
				.setUnidirectional(true)
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
		RewriterRule ruleAddAssoc = new RewriterRuleBuilder()
				.setUnidirectional(false)
				.withInstruction("+")
					.addOp("a")
						.ofType("float")
					.addOp("b")
						.ofType("float")
					.as("a+b")
				.withInstruction("+")
					.addExistingOp("a+b")
					.addOp("c")
						.ofType("float")
					.asRootInstruction()
				.toInstruction("+")
					.addExistingOp("b")
					.addExistingOp("c")
					.as("b+c")
				.toInstruction("+")
					.addExistingOp("a")
					.addExistingOp("b+c")
					.asRootInstruction()
				.build();
		RewriterRule ruleMulCommut = new RewriterRuleBuilder()
				.setUnidirectional(true)
				.withInstruction("*")
					.addOp("a")
						.ofType("float")
					.addOp("b")
						.ofType("float")
					.as("a*b")
					.asRootInstruction()
				.toInstruction("*")
					.addExistingOp("b")
					.addExistingOp("a")
					.as("b*a")
					.asRootInstruction()
				.build();
		RewriterRule ruleMulAssoc = new RewriterRuleBuilder()
				.setUnidirectional(false)
				.withInstruction("*")
					.addOp("a")
						.ofType("float")
					.addOp("b")
						.ofType("float")
					.as("a*b")
				.withInstruction("*")
					.addExistingOp("a*b")
					.addOp("c")
						.ofType("float")
					.asRootInstruction()
				.toInstruction("*")
					.addExistingOp("b")
					.addExistingOp("c")
					.as("b*c")
				.toInstruction("*")
					.addExistingOp("a")
					.addExistingOp("b*c")
					.asRootInstruction()
				.build();
		RewriterRule ruleDistrib = new RewriterRuleBuilder()
				.setUnidirectional(false)
				.withInstruction("*")
					.addOp("a")
						.ofType("float")
					.addOp("c")
						.ofType("float")
					.as("a*c")
				.withInstruction("*")
					.addOp("b")
						.ofType("float")
					.addExistingOp("c")
					.as("b*c")
				.withInstruction("+")
					.addExistingOp("a*c")
					.addExistingOp("b*c")
					.asRootInstruction()
				.toInstruction("+")
					.addExistingOp("a")
					.addExistingOp("b")
					.as("a+b")
				.toInstruction("*")
					.addExistingOp("a+b")
					.addExistingOp("c")
					.asRootInstruction()
				.build();

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleAddCommut);
		rules.add(ruleAddAssoc);
		rules.add(ruleMulCommut);
		rules.add(ruleMulAssoc);
		rules.add(ruleDistrib);

		ruleSet = new RewriterRuleSet(rules);
	}

	public static void main(String[] args) {

		System.out.println("Rules: ");

		/*RewriterInstruction instr = new RewriterRuleBuilder()
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
				.buildDAG();*/

		RewriterInstruction instr = new RewriterRuleBuilder()
				.asDAGBuilder()
				.withInstruction("*")
					.addOp("c")
						.ofType("float")
					.addOp("a")
						.ofType("float")
					.as("c*a")
				.withInstruction("*")
					.addOp("b")
						.ofType("float")
					.addExistingOp("a")
					.as("b*a")
				.withInstruction("+")
					.addExistingOp("c*a")
					.addExistingOp("b*a")
					.asRootInstruction()
				.buildDAG();

		RewriterDatabase db = new RewriterDatabase();
		db.insertEntry(instr);

		ArrayList<RewriterRuleSet.ApplicableRule> applicableRules = ruleSet.findApplicableRules(instr);
		PriorityQueue<RewriterQueuedTransformation> queue = applicableRules.stream().map(r -> new RewriterQueuedTransformation(instr, r)).sorted().collect(Collectors.toCollection(PriorityQueue::new));

		RewriterQueuedTransformation current = queue.poll();

		while (current != null) {
			System.out.println("Applying: " + current.rule.rule + " (" + current.rule.matches.size() + ")");
			for (RewriterStatement.MatchingSubexpression match : current.rule.matches) {
				// TODO: Or here is something wrong
				RewriterInstruction transformed = current.rule.forward ? current.rule.rule.applyForward(match, current.root, false) : current.rule.rule.applyBackward(match, current.root, false);

				if (!db.insertEntry(transformed)) // TODO: I think (a*c)+(b*c) equals (c*a)+(b*c) which disregards valid transformations
				{
					System.out.println("Skip: " + transformed);
					break; // Then this DAG has already been visited
				}

				System.out.println("Transformation: " + transformed);
				System.out.println("Cost: " + transformed.getCost());

				queue.addAll(ruleSet.findApplicableRules(instr).stream().map(r -> new RewriterQueuedTransformation(instr, r)).collect(Collectors.toList()));
			}

			current = queue.poll();
		}

		//applicableRules.forEach(System.out::println);

		/*ArrayList<RewriterStatement.MatchingSubexpression> matches = new ArrayList<>();
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
			//System.out.println(instr.linksToString());
			//System.out.println(instr);
		}*/

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
