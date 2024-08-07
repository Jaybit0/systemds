package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.HashMap;
import java.util.Map;

public class RewriterRule {

	private final String name;
	private final RewriterStatement fromRoot;
	private final RewriterStatement toRoot;
	private final boolean unidirectional;

	public RewriterRule(String name, RewriterStatement fromRoot, RewriterStatement toRoot, boolean unidirectional) {
		this.name = name;
		this.fromRoot = fromRoot;
		this.toRoot = toRoot;
		this.unidirectional = unidirectional;
	}

	public String getName() {
		return name;
	}

	public RewriterStatement getStmt1() {
		return fromRoot;
	}

	public RewriterStatement getStmt2() {
		return toRoot;
	}

	public boolean isUnidirectional() {
		return unidirectional;
	}

	public RewriterStatement applyForward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace) {
		return inplace ? applyInplace(match, rootNode, toRoot) : apply(match, rootNode, toRoot);
	}

	/*public RewriterInstruction applyForward(RewriterInstruction mRoot, RewriterInstruction mRootParent, DualHashBidiMap<RewriterStatement, RewriterStatement> assoc) {
		return apply(mRoot, mRootParent, assoc, toRoot);
	}*/

	public RewriterStatement applyBackward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace) {
		return inplace ? applyInplace(match, rootNode, fromRoot) : apply(match, rootNode, fromRoot);
	}

	/*public RewriterInstruction applyBackward(RewriterInstruction mRoot, RewriterInstruction mRootParent, DualHashBidiMap<RewriterStatement, RewriterStatement> assoc, boolean inplace) {
		return applyInplace(mRoot, mRootParent, assoc, fromRoot);
	}*/

	private RewriterStatement apply(RewriterStatement.MatchingSubexpression match, RewriterStatement rootInstruction, RewriterStatement dest) {
		if (match.getMatchParent() == null || match.getMatchParent() == match.getMatchRoot()) {
			final Map<RewriterStatement, RewriterStatement> createdObjects = new HashMap<>();
			RewriterStatement cpy = dest.nestedCopyOrInject(createdObjects, obj -> {
				RewriterStatement assoc = match.getAssocs().get(obj);
				if (assoc != null) {
					RewriterStatement assocCpy = createdObjects.get(assoc);
					if (assocCpy == null) {
						assocCpy = assoc.nestedCopyOrInject(createdObjects, obj2 -> null);
						createdObjects.put(assoc, assocCpy);
					}
					return assocCpy;
				}
				return null;
			});
			RewriterStatement tmp = cpy.simplify();
			if (tmp != null)
				cpy = tmp;
			cpy.prepareForHashing();
			cpy.recomputeHashCodes();
			return cpy;
		}

		final Map<RewriterStatement, RewriterStatement> createdObjects = new HashMap<>();
		RewriterStatement cpy2 = rootInstruction.nestedCopyOrInject(createdObjects, obj2 -> {
			if (obj2 == match.getMatchRoot()) {
				RewriterStatement cpy = dest.nestedCopyOrInject(createdObjects, obj -> {
					RewriterStatement assoc = match.getAssocs().get(obj);
					if (assoc != null) {
						RewriterStatement assocCpy = createdObjects.get(assoc);
						if (assocCpy == null) {
							assocCpy = assoc.nestedCopyOrInject(createdObjects, obj3 -> null);
							createdObjects.put(assoc, assocCpy);
						}
						return assocCpy;
					}
					return null;
				});
				createdObjects.put(obj2, cpy);
				return cpy;
			}
			return null;
		});
		RewriterStatement tmp = cpy2.simplify();
		if (tmp != null)
			cpy2 = tmp;
		cpy2.prepareForHashing();
		cpy2.recomputeHashCodes();
		return cpy2;
	}

	private RewriterStatement applyInplace(RewriterStatement.MatchingSubexpression match, RewriterStatement rootInstruction, RewriterStatement dest) {
		if (match.getMatchParent() == null || match.getMatchParent() == match.getMatchRoot()) {
			RewriterStatement cpy = dest.nestedCopyOrInject(new HashMap<>(), obj -> match.getAssocs().get(obj));
			RewriterStatement cpy2 = cpy.simplify();
			if (cpy2 != null)
				cpy = cpy2;
			cpy.prepareForHashing();
			cpy.recomputeHashCodes();
			return cpy;
		}

		/*int parentSize = match.getMatchParent().getOperands().size();

		ArrayList<RewriterStatement> operands = match.getMatchParent().getOperands();
		for (int i = 0; i < operands.size(); i++) {
			if (operands.get(i) == )
		}*/
		match.getMatchParent().getOperands().set(match.getRootIndex(), dest.nestedCopyOrInject(new HashMap<>(), obj -> match.getAssocs().get(obj)));
		RewriterStatement out = rootInstruction.simplify();
		if (out != null)
			out = rootInstruction;
		rootInstruction.prepareForHashing();
		rootInstruction.recomputeHashCodes();
		return rootInstruction;
		/*if (rootNode != null && mRoot != rootNode) {
			if (rootNode.getLinks() == null)
				rootNode.withLinks(new DualHashBidiMap<>(assoc));
			else
				RewriterStatement.insertLinks(rootNode.getLinks(), assoc);

			RewriterStatement.insertLink(rootNode.getLinks(), mRoot, dest);
		} else {
			mRoot.injectData(dest);

			if (mRoot.getLinks() == null)
				mRoot.withLinks(new DualHashBidiMap<>(assoc));
			else
				RewriterStatement.insertLinks(mRoot.getLinks(), assoc);
		}*/

		//return rootNode;
	}

	public String toString() {
		return fromRoot.toString() + " <=> " + toRoot.toString();
	}
}
