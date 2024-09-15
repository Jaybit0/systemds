package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class RewriterMain2 {

	public static void main(String[] args) {
		HashMap<String, List<String>> propertyList = new HashMap<>() {
			{
				put("+", Arrays.asList("RowSelectPushableBinaryInstruction"));
				put("-", Arrays.asList("RowSelectPushableBinaryInstruction"));
				//put("index", Arrays.asList());
			}
		};

		RewriterRuleSet ruleSet = RewriterRuleSet.selectionPushdown;

		RewriterInstruction instr = RewriterExamples.getSelectionPushdownExample3();
		instr.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(List.of("RowSelectPushableBinaryInstruction"), ruleSet.getContext()));

		RewriterInstruction current = instr;

		RewriterRuleSet.ApplicableRule rule = ruleSet.findFirstApplicableRule(current);

		while (rule != null) {
			System.out.println(current);

			current = (RewriterInstruction) rule.rule.apply(rule.matches.get(0), current, rule.forward, true);

			rule = ruleSet.findFirstApplicableRule(current);
		}

		System.out.println(current);
	}
}
