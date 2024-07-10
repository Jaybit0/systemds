package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;

public class RewriterRule {
	private RewriterStatement fromRoot;
	private RewriterStatement toRoot;

	public RewriterRule(RewriterStatement fromRoot, RewriterStatement toRoot) {
		this.fromRoot = fromRoot;
		this.toRoot = toRoot;
	}
}
