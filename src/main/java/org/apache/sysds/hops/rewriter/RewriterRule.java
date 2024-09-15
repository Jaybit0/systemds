package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.spark.sql.catalyst.expressions.Exp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RewriterRule extends AbstractRewriterRule {

	private final RuleContext ctx;
	private final String name;
	private final RewriterStatement fromRoot;
	private final RewriterStatement toRoot;
	private final HashMap<RewriterStatement, LinkObject> linksStmt1ToStmt2; // Contains the explicit links a transformation has (like instructions, (a+b)-c = a+(b-c), but '+' and '-' are the same instruction still [important if instructions have metadata])
	private final HashMap<RewriterStatement, LinkObject> linksStmt2ToStmt1;
	private final boolean unidirectional;

	public RewriterRule(final RuleContext ctx, String name, RewriterStatement fromRoot, RewriterStatement toRoot, boolean unidirectional, HashMap<RewriterStatement, LinkObject> linksStmt1ToStmt2, HashMap<RewriterStatement, LinkObject> linksStmt2ToStmt1) {
		this.ctx = ctx;
		this.name = name;
		this.fromRoot = fromRoot;
		this.toRoot = toRoot;
		this.unidirectional = unidirectional;
		this.linksStmt1ToStmt2 = linksStmt1ToStmt2;
		this.linksStmt2ToStmt1 = linksStmt2ToStmt1;
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

	public HashMap<RewriterStatement, LinkObject> getForwardLinks() {
		return linksStmt1ToStmt2;
	}

	public HashMap<RewriterStatement, LinkObject> getBackwardLinks() {
		return linksStmt2ToStmt1;
	}

	public RewriterStatement apply(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean forward, boolean inplace) {
		return forward ? applyForward(match, rootNode, inplace) : applyBackward(match, rootNode, inplace);
	}

	public RewriterStatement applyForward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace) {
		return inplace ? applyInplace(match, rootNode, toRoot) : apply(match, rootNode, toRoot);
	}

	public RewriterStatement applyBackward(RewriterStatement.MatchingSubexpression match, RewriterInstruction rootNode, boolean inplace) {
		return inplace ? applyInplace(match, rootNode, fromRoot) : apply(match, rootNode, fromRoot);
	}

	@Override
	public boolean matchStmt1(RewriterInstruction stmt, ArrayList<RewriterStatement.MatchingSubexpression> arr, boolean findFirst) {
		return getStmt1().matchSubexpr(ctx, stmt, null, -1, arr, new DualHashBidiMap<>(), true, false, findFirst, null, linksStmt1ToStmt2);
	}

	@Override
	public boolean matchStmt2(RewriterInstruction stmt, ArrayList<RewriterStatement.MatchingSubexpression> arr, boolean findFirst) {
		return getStmt2().matchSubexpr(ctx, stmt, null, -1, arr, new DualHashBidiMap<>(), true, false, findFirst, null, linksStmt2ToStmt1);
	}

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
			RewriterStatement tmp = cpy.simplify(ctx);
			if (tmp != null)
				cpy = tmp;

			match.getLinks().forEach(lnk -> lnk.newStmt.replaceAll(createdObjects::get));
			match.getLinks().forEach(lnk -> lnk.transferFunction.accept(lnk));

			cpy.prepareForHashing();
			cpy.recomputeHashCodes();

			return cpy;
		}

		final Map<RewriterStatement, RewriterStatement> createdObjects = new HashMap<>();
		RewriterStatement cpy2 = rootInstruction.nestedCopyOrInject(createdObjects, obj2 -> {
			if (obj2 == match.getMatchRoot()) {
				RewriterStatement cpy = dest.nestedCopyOrInject(createdObjects, obj -> {
					RewriterStatement assoc = match.getAssocs().get(obj);
					/*for (Map.Entry<RewriterStatement, RewriterStatement> mAssoc : match.getAssocs().entrySet())
						System.out.println(mAssoc.getKey() + " -> " + mAssoc.getValue());*/
					if (assoc != null) {
						RewriterStatement assocCpy = createdObjects.get(assoc);
						if (assocCpy == null) {
							assocCpy = assoc.nestedCopyOrInject(createdObjects, obj3 -> null);
							createdObjects.put(assoc, assocCpy);
						}
						return assocCpy;
					}
					//System.out.println("ObjInner: " + obj);
					return null;
				});
				createdObjects.put(obj2, cpy);
				return cpy;
			}
			//System.out.println("Obj: " + obj2);
			return null;
		});
		RewriterStatement tmp = cpy2.simplify(ctx);
		if (tmp != null)
			cpy2 = tmp;

		match.getLinks().forEach(lnk -> lnk.newStmt.replaceAll(createdObjects::get));
		match.getLinks().forEach(lnk -> lnk.transferFunction.accept(lnk));

		cpy2.prepareForHashing();
		cpy2.recomputeHashCodes();
		return cpy2;
	}

	// TODO: Not working right now
	private RewriterStatement applyInplace(RewriterStatement.MatchingSubexpression match, RewriterStatement rootInstruction, RewriterStatement dest) {
		if (match.getMatchParent() == null || match.getMatchParent() == match.getMatchRoot()) {
			final Map<RewriterStatement, RewriterStatement> createdObjects = new HashMap<>();
			RewriterStatement cpy = dest.nestedCopyOrInject(createdObjects, obj -> match.getAssocs().get(obj));
			RewriterStatement cpy2 = cpy.simplify(ctx);
			if (cpy2 != null)
				cpy = cpy2;

			match.getLinks().forEach(lnk -> lnk.newStmt.replaceAll(createdObjects::get));
			match.getLinks().forEach(lnk -> lnk.transferFunction.accept(lnk));

			cpy.prepareForHashing();
			cpy.recomputeHashCodes();
			return cpy;
		}

		/*int parentSize = match.getMatchParent().getOperands().size();

		ArrayList<RewriterStatement> operands = match.getMatchParent().getOperands();
		for (int i = 0; i < operands.size(); i++) {
			if (operands.get(i) == )
		}*/
		final Map<RewriterStatement, RewriterStatement> createdObjects = new HashMap<>();
		match.getMatchParent().getOperands().set(match.getRootIndex(), dest.nestedCopyOrInject(createdObjects, obj -> match.getAssocs().get(obj)));
		RewriterStatement out = rootInstruction.simplify(ctx);
		if (out != null)
			out = rootInstruction;

		match.getLinks().forEach(lnk -> lnk.newStmt.replaceAll(createdObjects::get));
		match.getLinks().forEach(lnk -> lnk.transferFunction.accept(lnk));

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

	static class LinkObject {
		List<RewriterStatement> stmt;
		Consumer<ExplicitLink> transferFunction;

		public LinkObject() {
			stmt = new ArrayList<>(2);
		}

		public LinkObject(List<RewriterStatement> stmt, Consumer<ExplicitLink> transferFunction) {
			this.stmt = stmt;
			this.transferFunction = transferFunction;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < stmt.size(); i++) {
				if (i != 0)
					sb.append(", ");
				sb.append(stmt.get(i));
			}
			return sb.toString();
		}

		// TODO: Change
		@Override
		public boolean equals(Object o) {
			return o instanceof LinkObject && ((LinkObject)o).stmt == stmt;
		}

		@Override
		public int hashCode() {
			return stmt.hashCode();
		}
	}

	static class ExplicitLink {
		final RewriterStatement oldStmt;
		List<RewriterStatement> newStmt;
		final Consumer<ExplicitLink> transferFunction;

		public ExplicitLink(RewriterStatement oldStmt, List<RewriterStatement> newStmt, Consumer<ExplicitLink> transferFunction) {
			this.oldStmt = oldStmt;
			this.newStmt = new ArrayList<>(newStmt);
			this.transferFunction = transferFunction;
		}
	}
}
