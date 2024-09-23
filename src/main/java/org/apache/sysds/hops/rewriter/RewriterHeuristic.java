package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.annotation.Nullable;
import java.util.function.Function;

public class RewriterHeuristic implements RewriterHeuristicTransformation {
	private final RewriterRuleSet ruleSet;
	//private final List<String> desiredProperties;

	public RewriterHeuristic(RewriterRuleSet ruleSet/*, List<String> desiredProperties*/) {
		this.ruleSet = ruleSet;
		//this.desiredProperties = desiredProperties;
	}

	public RewriterStatement apply(RewriterStatement current) {
		return apply(current, null);
	}

	public RewriterStatement apply(RewriterStatement current, @Nullable Function<RewriterStatement, Boolean> handler) {
		return apply(current, handler, new MutableBoolean(false));
	}

	public RewriterStatement apply(RewriterStatement currentStmt, @Nullable Function<RewriterStatement, Boolean> handler, MutableBoolean foundRewrite) {
		RuleContext.currentContext = ruleSet.getContext();

		//current.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(desiredProperties, ruleSet.getContext()));

		if (handler != null && !handler.apply(currentStmt))
			return currentStmt;

		if (!(currentStmt instanceof RewriterInstruction))
			return currentStmt;

		RewriterInstruction current = (RewriterInstruction) currentStmt;

		RewriterRuleSet.ApplicableRule rule = ruleSet.findFirstApplicableRule(current);

		if (rule != null)
			foundRewrite.setValue(true);

		while (rule != null) {
			currentStmt = rule.rule.apply(rule.matches.get(0), current, rule.forward, true);

			if (handler != null && !handler.apply(currentStmt))
				break;

			if (!(currentStmt instanceof RewriterInstruction))
				break;

			current = (RewriterInstruction)currentStmt;

			rule = ruleSet.findFirstApplicableRule(current);
		}

		return currentStmt;
	}
}
