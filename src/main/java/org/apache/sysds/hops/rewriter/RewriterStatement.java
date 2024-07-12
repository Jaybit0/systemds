package org.apache.sysds.hops.rewriter;

import java.util.HashMap;
import java.util.List;

public interface RewriterStatement {

	class MatchingSubexpression {
		private final RewriterInstruction matchRoot;
		private final RewriterInstruction matchParent;
		private final HashMap<RewriterStatement, RewriterStatement> assocs;

		public MatchingSubexpression(RewriterInstruction matchRoot, RewriterInstruction matchParent, HashMap<RewriterStatement, RewriterStatement> assocs) {
			this.matchRoot = matchRoot;
			this.matchParent = matchParent;
			this.assocs = assocs;
		}

		public RewriterInstruction getMatchRoot() {
			return matchRoot;
		}

		public RewriterInstruction getMatchParent() {
			return matchParent;
		}

		public HashMap<RewriterStatement, RewriterStatement> getAssocs() {
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
	String toStringWithLinking(HashMap<RewriterStatement, RewriterStatement> links);

	// Returns the root of the matching sub-statement, null if there is no match
	boolean match(RewriterStatement stmt, HashMap<RewriterStatement, RewriterStatement> dependencyMap, HashMap<RewriterStatement, RewriterStatement> links);
	default boolean matchSubexpr(RewriterInstruction root, RewriterInstruction parent, List<MatchingSubexpression> matches, HashMap<RewriterStatement, RewriterStatement> dependencyMap, HashMap<RewriterStatement, RewriterStatement> links) {
		if (match(root, dependencyMap, links)) {
			matches.add(new MatchingSubexpression(root, parent, dependencyMap));
			dependencyMap = null;
		}

		if (dependencyMap == null)
			dependencyMap = new HashMap<>();
		else
			dependencyMap.clear();


		for (RewriterStatement stmt : root.getOperands()) {
			stmt = links.getOrDefault(stmt, stmt);
			if (stmt instanceof RewriterInstruction)
				matchSubexpr((RewriterInstruction)stmt, root, matches, dependencyMap, links);
		}

		return !matches.isEmpty();
	}
}
