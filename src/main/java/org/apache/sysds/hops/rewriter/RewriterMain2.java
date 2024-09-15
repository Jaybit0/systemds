package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class RewriterMain2 {

	public static void main(String[] args) {
		StringBuilder builder = new StringBuilder();

		builder.append("RowSelectPushableBinaryInstruction(MATRIX,MATRIX)::MATRIX\n");
		builder.append("impl +\n");
		builder.append("impl -\n");
		builder.append("impl min\n");
		builder.append("impl max\n");
		builder.append("rowSelect(MATRIX,INT,INT)::MATRIX\n");
		builder.append("min(INT,INT)::INT\n");
		builder.append("max(INT,INT)::INT");

		RuleContext ctx = RuleContext.createContext(builder.toString());
		System.out.println(ctx.instrTypes);
		System.out.println(ctx.instrProperties);

		//RewriterRuleSet ruleSet = RewriterRuleSet.selectionPushdown;
		RewriterRuleSet ruleSet = RewriterRuleSet.buildSelectionPushdownRuleSet(ctx);

		RewriterInstruction instr = RewriterExamples.selectionPushdownExample2();
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
