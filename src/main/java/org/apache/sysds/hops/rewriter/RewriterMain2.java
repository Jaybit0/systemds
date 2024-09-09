package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.PriorityQueue;

public class RewriterMain2 {

	public static void main(String[] args) {
		RewriterRuleSet ruleSet = RewriterRuleSet.selectionPushdown;

		RewriterInstruction instr = RewriterExamples.selectionPushdownExample2();

		RewriterInstruction current = instr;

		RewriterRuleSet.ApplicableRule rule = ruleSet.findFirstApplicableRule(current);

		while (rule != null) {
			System.out.println(current);

			if (rule.forward)
				current = (RewriterInstruction)rule.rule.applyForward(rule.matches.get(0), current, true);
			else
				current = (RewriterInstruction)rule.rule.applyBackward(rule.matches.get(0), current, true);

			rule = ruleSet.findFirstApplicableRule(current);
		}

		System.out.println(current);
	}
}
