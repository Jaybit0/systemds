package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface RewriterStatement {

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


	class MatchingSubexpression {
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

	String getId();
	String getResultingDataType();
	boolean isLiteral();
	void consolidate();
	boolean isConsolidated();
	@Deprecated
	RewriterStatement clone();
	RewriterStatement copyNode();
	// Performs a nested copy until a condition is met
	RewriterStatement nestedCopyOrInject(Map<RewriterStatement, RewriterStatement> copiedObjects, Function<RewriterStatement, RewriterStatement> injector);
	//String toStringWithLinking(int dagId, DualHashBidiMap<RewriterStatementLink, RewriterStatementLink> links);

	// Returns the root of the matching sub-statement, null if there is no match
	boolean match(RewriterStatement stmt, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap);
	int recomputeHashCodes();
	long getCost();
	default boolean matchSubexpr(RewriterInstruction root, RewriterInstruction parent, int rootIndex, List<MatchingSubexpression> matches, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap) {
		if (dependencyMap == null)
			dependencyMap = new DualHashBidiMap<>();

		if (match(root, dependencyMap)) {
			matches.add(new MatchingSubexpression(root, parent, rootIndex, dependencyMap));
			dependencyMap = null;
		}

		if (dependencyMap == null)
			dependencyMap = new DualHashBidiMap<>();
		else
			dependencyMap.clear();

		int idx = 0;
		for (RewriterStatement stmt : root.getOperands()) {
			if (stmt instanceof RewriterInstruction)
				matchSubexpr((RewriterInstruction)stmt, root, idx, matches, dependencyMap);
			idx++;
		}

		return !matches.isEmpty();
	}
}
