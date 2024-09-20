package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.mutable.MutableBoolean;

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

	public RewriterInstruction apply(RewriterInstruction current) {
		return apply(current, null);
	}

	public RewriterInstruction apply(RewriterInstruction current, @Nullable Function<RewriterInstruction, Boolean> handler) {
		return apply(current, handler, new MutableBoolean(false));
	}

	public RewriterInstruction apply(RewriterInstruction current, @Nullable Function<RewriterInstruction, Boolean> handler, MutableBoolean foundRewrite) {
		RuleContext.currentContext = ruleSet.getContext();
		current.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(desiredProperties, ruleSet.getContext()));

		if (handler != null && !handler.apply(current))
			return current;

		RewriterRuleSet.ApplicableRule rule = ruleSet.findFirstApplicableRule(current);

		if (rule != null)
			foundRewrite.setValue(true);

		while (rule != null) {
			current = (RewriterInstruction) rule.rule.apply(rule.matches.get(0), current, rule.forward, true);

			if (handler != null && !handler.apply(current))
				break;

			rule = ruleSet.findFirstApplicableRule(current);
		}

		return current;
	}
}
