package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class RewriterStatement implements Comparable<RewriterStatement> {

	protected int rid = 0;
	protected int refCtr = 0;

	static RewriterStatementLink resolveNode(RewriterStatementLink link, DualHashBidiMap<RewriterStatementLink, RewriterStatementLink> links) {
		if (links == null)
			return link;

		RewriterStatementLink next = links.getOrDefault(link, link);
		while (!next.equals(link)) {
			link = next;
			next = links.getOrDefault(next, next);
		}
		return next;
	}

	static void insertLinks(DualHashBidiMap<RewriterStatementLink, RewriterStatementLink> links, Map<RewriterStatementLink, RewriterStatementLink> inserts) {
		inserts.forEach((key, value) -> insertLink(links, key, value));
	}

	static void insertLink(DualHashBidiMap<RewriterStatementLink, RewriterStatementLink> links, RewriterStatementLink key, RewriterStatementLink value) {
		RewriterStatementLink origin = links.removeValue(key);
		RewriterStatementLink dest = links.remove(value);
		origin = origin != null ? origin : key;
		dest = dest != null ? dest : value;

		//System.out.println(" + " + origin.stmt.toStringWithLinking(links) + " -> " + dest.stmt.toStringWithLinking(links));

		if (origin != dest)
			links.put(origin, dest);
	}


	public static class MatchingSubexpression {
		private final RewriterInstruction matchRoot;
		private final RewriterInstruction matchParent;
		private final int rootIndex;
		private final DualHashBidiMap<RewriterStatement, RewriterStatement> assocs;

		public MatchingSubexpression(RewriterInstruction matchRoot, RewriterInstruction matchParent, int rootIndex, DualHashBidiMap<RewriterStatement, RewriterStatement> assocs) {
			this.matchRoot = matchRoot;
			this.matchParent = matchParent;
			this.assocs = assocs;
			this.rootIndex = rootIndex;
		}

		public boolean isRootInstruction() {
			return matchParent == null || matchParent == matchRoot;
		}

		public RewriterInstruction getMatchRoot() {
			return matchRoot;
		}

		public RewriterInstruction getMatchParent() {
			return matchParent;
		}

		public int getRootIndex() {
			return rootIndex;
		}

		public DualHashBidiMap<RewriterStatement, RewriterStatement> getAssocs() {
			return assocs;
		}
	}

	public abstract String getId();
	public abstract String getResultingDataType(final RuleContext ctx);
	public abstract boolean isLiteral();
	public abstract Object getLiteral();

	public void setLiteral(Object literal) {
		throw new IllegalArgumentException("This class does not support setting literals");
	}
	public abstract void consolidate(final RuleContext ctx);
	public abstract boolean isConsolidated();
	@Deprecated
	public abstract RewriterStatement clone();
	public abstract RewriterStatement copyNode();
	// Performs a nested copy until a condition is met
	public abstract RewriterStatement nestedCopyOrInject(Map<RewriterStatement, RewriterStatement> copiedObjects, Function<RewriterStatement, RewriterStatement> injector);
	//String toStringWithLinking(int dagId, DualHashBidiMap<RewriterStatementLink, RewriterStatementLink> links);

	// Returns the root of the matching sub-statement, null if there is no match
	public abstract boolean match(final RuleContext ctx, RewriterStatement stmt, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues);
	public abstract int recomputeHashCodes();
	public abstract long getCost();
	public abstract RewriterStatement simplify(final RuleContext ctx);
	public abstract RewriterStatement as(String id);

	@Nullable
	public List<RewriterStatement> getOperands() {
		return null;
	}
	public boolean matchSubexpr(final RuleContext ctx, RewriterInstruction root, RewriterInstruction parent, int rootIndex, List<MatchingSubexpression> matches, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues, boolean findFirst) {
		if (dependencyMap == null)
			dependencyMap = new DualHashBidiMap<>();
		else
			dependencyMap.clear();

		boolean foundMatch = match(ctx, root, dependencyMap, literalsCanBeVariables, ignoreLiteralValues);

		if (foundMatch) {
			matches.add(new MatchingSubexpression(root, parent, rootIndex, dependencyMap));
			dependencyMap = null;

			if (findFirst)
				return true;
		}

		int idx = 0;

		for (RewriterStatement stmt : root.getOperands()) {
			if (stmt instanceof RewriterInstruction)
				if (matchSubexpr(ctx, (RewriterInstruction) stmt, root, idx, matches, dependencyMap, literalsCanBeVariables, ignoreLiteralValues, findFirst)) {
					dependencyMap = new DualHashBidiMap<>();
					foundMatch = true;

					if (findFirst)
						return true;
				}
			idx++;
		}

		return foundMatch;
	}

	public void prepareForHashing() {
		resetRefCtrs();
		computeRefCtrs();
		resetIds();
		computeIds(1);
	}

	protected void resetRefCtrs() {
		refCtr = 0;
		if (getOperands() != null)
			getOperands().forEach(RewriterStatement::resetRefCtrs);
	}

	protected void computeRefCtrs() {
		refCtr++;
		if (getOperands() != null)
			getOperands().forEach(RewriterStatement::computeRefCtrs);
	}

	protected void resetIds() {
		rid = 0;
		if (getOperands() != null)
			getOperands().forEach(RewriterStatement::resetIds);
	}

	protected int computeIds(int id) {
		if (rid != 0)
			return id;

		rid = id++;

		if (getOperands() != null) {
			for (RewriterStatement stmt : getOperands())
				id = stmt.computeIds(id);
		}

		return id;
	}

	@Override
	public int compareTo(@NotNull RewriterStatement o) {
		return Long.compare(getCost(), o.getCost());
	}
}
