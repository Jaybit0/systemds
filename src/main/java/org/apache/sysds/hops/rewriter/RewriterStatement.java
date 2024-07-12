package org.apache.sysds.hops.rewriter;

import java.util.HashMap;
import java.util.List;

public interface RewriterStatement {

	class MatchingSubexpression {
		private final RewriterStatement matchRoot;
		private final HashMap<RewriterDataType, RewriterStatement> assocs;

		public MatchingSubexpression(RewriterStatement matchRoot, HashMap<RewriterDataType, RewriterStatement> assocs) {
			this.matchRoot = matchRoot;
			this.assocs = assocs;
		}

		public RewriterStatement getMatchRoot() {
			return matchRoot;
		}

		public HashMap<RewriterDataType, RewriterStatement> getAssocs() {
			return assocs;
		}
	}

	String getId();
	String getResultingDataType();
	boolean isLiteral();
	void consolidate();
	boolean isConsolidated();

	// Returns the root of the matching sub-statement, null if there is no match
	boolean match(RewriterStatement stmt, HashMap<RewriterDataType, RewriterStatement> dependencyMap);
	default boolean matchSubexpr(RewriterStatement root, List<MatchingSubexpression> matches, HashMap<RewriterDataType, RewriterStatement> dependencyMap) {
		if (match(root, dependencyMap)) {
			matches.add(new MatchingSubexpression(root, dependencyMap));
			dependencyMap = null;
		}

		if (root instanceof RewriterInstruction) {
			if (dependencyMap == null)
				dependencyMap = new HashMap<>();
			else
				dependencyMap.clear();

			RewriterInstruction instr = (RewriterInstruction) root;

			for (RewriterStatement stmt : instr.getOperands()) {
				matchSubexpr(stmt, matches, dependencyMap);
			}
		}

		return !matches.isEmpty();
	}
}
