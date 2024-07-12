package org.apache.sysds.hops.rewriter;

import java.util.HashMap;

public class RewriterRule {
	private final RewriterInstruction fromRoot;
	private final RewriterInstruction toRoot;

	public RewriterRule(RewriterInstruction fromRoot, RewriterInstruction toRoot) {
		this.fromRoot = fromRoot;
		this.toRoot = toRoot;
	}

	public RewriterInstruction getStmt1() {
		return fromRoot;
	}

	public RewriterInstruction getStmt2() {
		return toRoot;
	}

	public RewriterStatement applyForward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode) {
		return applyForward(match.getMatchRoot(), rootNode, match.getAssocs());
	}

	public RewriterStatement applyForward(RewriterInstruction mRoot, RewriterInstruction mRootParent, HashMap<RewriterStatement, RewriterStatement> assoc) {
		return apply(mRoot, mRootParent, assoc, toRoot);
	}

	public RewriterStatement applyBackward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode) {
		return applyBackward(match.getMatchRoot(), rootNode, match.getAssocs());
	}

	public RewriterStatement applyBackward(RewriterInstruction mRoot, RewriterInstruction mRootParent, HashMap<RewriterStatement, RewriterStatement> assoc) {
		return apply(mRoot, mRootParent, assoc, fromRoot);
	}

	private RewriterStatement apply(RewriterInstruction mRoot, RewriterInstruction mRootParent, HashMap<RewriterStatement, RewriterStatement> assoc, RewriterInstruction dest) {
		if (mRootParent != null && mRoot != mRootParent) {
			if (mRootParent.getLinks() == null)
				mRootParent.withLinks(new HashMap<>(assoc.size() + 1));

			// TODO: Copy would be more efficient if generic types were the same
			assoc.forEach((key, value) -> mRootParent.getLinks().put(key, value));
			mRootParent.getLinks().put(mRoot, dest);
		} else {
			if (mRoot.getLinks() == null)
				mRoot.withLinks(new HashMap<>(assoc.size() + 1));

			// TODO: Copy would be more efficient if generic types were the same
			assoc.forEach((key, value) -> mRoot.getLinks().put(key, value));
		}

		return dest;
	}

	public String toString() {
		return fromRoot.toString() + " <=> " + toRoot.toString();
	}
}
