package org.apache.sysds.hops.rewriter.rules;

import org.apache.sysds.hops.rewriter.AbstractRewriterRule;
import org.apache.sysds.hops.rewriter.RewriterInstruction;
import org.apache.sysds.hops.rewriter.RewriterStatement;

public class SpecializedRewriterRule extends AbstractRewriterRule {

	@Override
	public String getName() {
		return null;
	}

	@Override
	public RewriterStatement getStmt1() {
		return null;
	}

	@Override
	public RewriterStatement getStmt2() {
		return null;
	}

	@Override
	public boolean isUnidirectional() {
		return false;
	}

	@Override
	public RewriterStatement applyForward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace) {
		return null;
	}

	@Override
	public RewriterStatement applyBackward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace) {
		return null;
	}
}
