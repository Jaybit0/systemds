package org.apache.sysds.hops.rewriter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public class RewriterHeuristic {
	private final RewriterRuleSet ruleSet;
	private final List<String> desiredProperties;

	public RewriterHeuristic(RewriterRuleSet ruleSet, List<String> desiredProperties) {
		this.ruleSet = ruleSet;
		this.desiredProperties = desiredProperties;
	}

	public RewriterInstruction apply(RewriterInstruction current, @Nullable Function<RewriterInstruction, Boolean> handler) {
		RuleContext.currentContext = ruleSet.getContext();
		current.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(desiredProperties, ruleSet.getContext()));

		if (handler != null && !handler.apply(current))
			return current;

		RewriterRuleSet.ApplicableRule rule = ruleSet.findFirstApplicableRule(current);

		while (rule != null) {
			current = (RewriterInstruction) rule.rule.apply(rule.matches.get(0), current, rule.forward, true);

			if (handler != null && !handler.apply(current))
				break;

			rule = ruleSet.findFirstApplicableRule(current);
		}

		return current;
	}
}
