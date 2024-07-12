package org.apache.sysds.hops.rewriter;

public class RewriterRule {
	private final RewriterStatement fromRoot;
	private final RewriterStatement toRoot;

	public RewriterRule(RewriterStatement fromRoot, RewriterStatement toRoot) {
		this.fromRoot = fromRoot;
		this.toRoot = toRoot;
	}

	public RewriterStatement getStmt1() {
		return fromRoot;
	}

	public RewriterStatement getStmt2() {
		return toRoot;
	}

	public String toString() {
		return fromRoot.toString() + " <=> " + toRoot.toString();
	}
}
