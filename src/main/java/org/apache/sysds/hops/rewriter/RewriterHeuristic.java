package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class RewriterHeuristic implements RewriterHeuristicTransformation {
	private final RewriterRuleSet ruleSet;
	private final boolean accelerated;
	//private final List<String> desiredProperties;

	public RewriterHeuristic(RewriterRuleSet ruleSet) {
		this(ruleSet, true);
	}

	public RewriterHeuristic(RewriterRuleSet ruleSet, boolean accelerated/*, List<String> desiredProperties*/) {
		this.ruleSet = ruleSet;
		this.accelerated = accelerated;
		//this.desiredProperties = desiredProperties;
	}

	public void forEachRuleSet(Consumer<RewriterRuleSet> consumer, boolean printNames) {
		consumer.accept(ruleSet);
	}

	public RewriterStatement apply(RewriterStatement current) {
		return apply(current, null);
	}

	public RewriterStatement apply(RewriterStatement current, @Nullable BiFunction<RewriterStatement, RewriterRule, Boolean> handler) {
		return apply(current, handler, new MutableBoolean(false));
	}

	public RewriterStatement apply(RewriterStatement currentStmt, @Nullable BiFunction<RewriterStatement, RewriterRule, Boolean> handler, MutableBoolean foundRewrite) {
		RuleContext.currentContext = ruleSet.getContext();

		//current.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(desiredProperties, ruleSet.getContext()));

		if (handler != null && !handler.apply(currentStmt, null))
			return currentStmt;

		if (!(currentStmt instanceof RewriterInstruction))
			return currentStmt;

		RewriterInstruction current = (RewriterInstruction) currentStmt;

		RewriterRuleSet.ApplicableRule rule;
		if (accelerated)
			rule = ruleSet.acceleratedFindFirst(current);
		else
			throw new NotImplementedException("Must use accelerated mode");//rule = ruleSet.findFirstApplicableRule(current);

		if (rule != null)
			foundRewrite.setValue(true);

		while (rule != null) {
			currentStmt = rule.rule.apply(rule.matches.get(0), current, rule.forward, true);

			if (handler != null && !handler.apply(currentStmt, rule.rule))
				break;

			if (!(currentStmt instanceof RewriterInstruction))
				break;

			current = (RewriterInstruction)currentStmt;

			if (accelerated)
				rule = ruleSet.acceleratedFindFirst(current);
			else
				throw new IllegalArgumentException("Must use accelerated mode!");//rule = ruleSet.findFirstApplicableRule(current);
		}

		return currentStmt;
	}

	@Override
	public String toString() {
		return ruleSet.toString();
	}
}