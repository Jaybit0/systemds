package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

public class RewriterMain {

	private static RewriterRuleSet ruleSet;
	private static RewriterRule distrib;
	private static RewriterRule commutMul;

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

		distrib = ruleDistrib;
		commutMul = ruleMulCommut;

		ArrayList<RewriterRule> rules = new ArrayList<>();
		rules.add(ruleAddCommut);
		rules.add(ruleAddAssoc);
		rules.add(ruleMulCommut);
		rules.add(ruleMulAssoc);
		rules.add(ruleDistrib);

		ruleSet = new RewriterRuleSet(rules);
	}

	public static void main(String[] args) {
		System.out.println(ruleSet);
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
					.addExistingOp("c")
					.as("b*c")
				.withInstruction("*")
					.addExistingOp("a")
					.addExistingOp("c")
					.as("a*c")
				.withInstruction("*")
					.addOp("d")
						.ofType("float")
					.addExistingOp("a")
					.as("d*a")
				.withInstruction("+")
					.addExistingOp("c*a")
					.addExistingOp("b*c")
					.as("par1")
				.withInstruction("+")
					.addExistingOp("par1")
					.addExistingOp("a*c")
					.as("par2")
				.withInstruction("+")
					.addExistingOp("par1")
					.addExistingOp("par2")
					.as("par3")
				.withInstruction("+")
					.addExistingOp("par3")
					.addExistingOp("d*a")
					.asRootInstruction()
				.buildDAG();

		RewriterInstruction optimum = instr;
		long optimalCost = instr.getCost();

		RewriterDatabase db = new RewriterDatabase();
		db.insertEntry(instr);

		long time = System.currentTimeMillis();

		ArrayList<RewriterRuleSet.ApplicableRule> applicableRules = ruleSet.findApplicableRules(instr);
		PriorityQueue<RewriterQueuedTransformation> queue = applicableRules.stream().map(r -> new RewriterQueuedTransformation(instr, r)).sorted().collect(Collectors.toCollection(PriorityQueue::new));

		RewriterQueuedTransformation current = queue.poll();
		long insertTime = 0;
		long findApplicableRulesTime = 0;
		HashSet<Integer> hashes = new HashSet<>();

		for (int i = 0; i < 10000000 && current != null; i++) {
			insertTime = 0;
			findApplicableRulesTime = 0;
			long total = System.nanoTime();
			long trans = 0;
			for (RewriterStatement.MatchingSubexpression match : current.rule.matches) {
				long delta = System.nanoTime();
				RewriterInstruction transformed = current.rule.forward ? current.rule.rule.applyForward(match, current.root, false) : current.rule.rule.applyBackward(match, current.root, false);
				hashes.add(transformed.hashCode());
				trans += System.nanoTime() - delta;

				delta = System.nanoTime();
				if (!db.insertEntry(transformed))
				{
					//System.out.println("Skip: " + transformed);
					//System.out.println("======");
					insertTime += System.nanoTime() - delta;
					break; // Then this DAG has already been visited
				}
				insertTime += System.nanoTime() - delta;

				/*System.out.println("Source: " + current.root);
				//System.out.println(current.rule);
				System.out.println("Transformed: " + transformed);
				System.out.println("Cost: " + transformed.getCost());
				System.out.println();
				System.out.println("=====");
				System.out.println();*/
				//System.out.println(transformed);

				/*System.out.println("Available transformations:");
				ruleSet.findApplicableRules(transformed).forEach(System.out::println);
				System.out.println("======");*/

				long newCost = transformed.getCost();
				if (newCost < optimalCost) {
					optimalCost = newCost;
					optimum = transformed;
				}

				delta = System.nanoTime();
				applicableRules = ruleSet.findApplicableRules(transformed);
				findApplicableRulesTime += System.nanoTime() - delta;

				if (applicableRules.size() > 0)
					queue.addAll(applicableRules.stream().map(r -> new RewriterQueuedTransformation(transformed, r)).collect(Collectors.toList()));
			}

			total = System.nanoTime() - total;

			if (i % 100 == 0)
				System.out.print("\r" + db.size() + " unique graphs (Opt: " + optimum + ", Cost: " + optimalCost + ", queueSize: " + queue.size() + ", insertTime: " + (insertTime / current.rule.matches.size()) + ", ruleFindTime: " + (findApplicableRulesTime / current.rule.matches.size()) + ", totalPerIt: " + (total / current.rule.matches.size()) + ")");

			current = queue.poll();
		}

		System.out.println();
		System.out.println("All possible transformations found in " + (System.currentTimeMillis() - time) + "ms");
		System.out.println("Original graph: " + instr);
		System.out.println("Original cost: " + instr.getCost());
		System.out.println("Optimum: " + optimum);
		System.out.println("Cost: " + optimalCost);
		System.out.println("Unique hashes: " + hashes.size());
	}
}
