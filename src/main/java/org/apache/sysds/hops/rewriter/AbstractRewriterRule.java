package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;

public abstract class AbstractRewriterRule {
	public abstract String getName();
	public abstract RewriterStatement getStmt1();
	public abstract RewriterStatement getStmt2();
	public abstract boolean isUnidirectional();
	public abstract RewriterStatement applyForward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace);
	public abstract RewriterStatement applyBackward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace);
	public abstract boolean matchStmt1(RewriterInstruction stmt, ArrayList<RewriterStatement.MatchingSubexpression> arr);
	public abstract boolean matchStmt2(RewriterInstruction stmt2, ArrayList<RewriterStatement.MatchingSubexpression> arr);
}
