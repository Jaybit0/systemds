package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class RewriterStatement implements Comparable<RewriterStatement> {

	protected int rid = 0;
	protected int refCtr = 0;

	protected HashMap<String, Object> meta = null;

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
		private final List<RewriterRule.ExplicitLink> links;

		public MatchingSubexpression(RewriterInstruction matchRoot, RewriterInstruction matchParent, int rootIndex, DualHashBidiMap<RewriterStatement, RewriterStatement> assocs, List<RewriterRule.ExplicitLink> links) {
			this.matchRoot = matchRoot;
			this.matchParent = matchParent;
			this.assocs = assocs;
			this.rootIndex = rootIndex;
			this.links = links;
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

		public List<RewriterRule.ExplicitLink> getLinks() {
			return links;
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
	public abstract boolean match(final RuleContext ctx, RewriterStatement stmt, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues, List<RewriterRule.ExplicitLink> links, final Map<RewriterStatement, RewriterRule.LinkObject> ruleLinks);
	public abstract int recomputeHashCodes(boolean recursively);
	public abstract long getCost();
	public abstract RewriterStatement simplify(final RuleContext ctx);
	public abstract RewriterStatement as(String id);
	public abstract String toString(final RuleContext ctx);
	public abstract boolean isArgumentList();

	@Nullable
	public List<RewriterStatement> getOperands() {
		return null;
	}

	public int recomputeHashCodes() {
		return recomputeHashCodes(true);
	}
	public boolean matchSubexpr(final RuleContext ctx, RewriterInstruction root, RewriterInstruction parent, int rootIndex, List<MatchingSubexpression> matches, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues, boolean findFirst, List<RewriterRule.ExplicitLink> links, final Map<RewriterStatement, RewriterRule.LinkObject> ruleLinks) {
		if (dependencyMap == null)
			dependencyMap = new DualHashBidiMap<>();
		else
			dependencyMap.clear();

		if (links == null)
			links = new ArrayList<>();
		else
			links.clear();

		boolean foundMatch = match(ctx, root, dependencyMap, literalsCanBeVariables, ignoreLiteralValues, links, ruleLinks);

		if (foundMatch) {
			matches.add(new MatchingSubexpression(root, parent, rootIndex, dependencyMap, links));
			dependencyMap = null;
			links = null;

			if (findFirst)
				return true;
		}

		int idx = 0;

		for (RewriterStatement stmt : root.getOperands()) {
			if (stmt instanceof RewriterInstruction)
				if (matchSubexpr(ctx, (RewriterInstruction) stmt, root, idx, matches, dependencyMap, literalsCanBeVariables, ignoreLiteralValues, findFirst, links, ruleLinks)) {
					dependencyMap = new DualHashBidiMap<>();
					links = new ArrayList<>();
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

	/**
	 * Traverses the DAG in post-order. If nodes with multiple parents exist, those are visited multiple times.
	 * If the function returns false, the sub-DAG of the current node will not be traversed.
	 * @param function
	 */
	public void forEachPostOrderWithDuplicates(Function<RewriterStatement, Boolean> function) {
		if (function.apply(this) && getOperands() != null)
			for (RewriterStatement stmt : getOperands())
				stmt.forEachPostOrderWithDuplicates(function);
	}

	@Override
	public int compareTo(@NotNull RewriterStatement o) {
		return Long.compare(getCost(), o.getCost());
	}

	public void putMeta(String key, Object value) {
		if (isConsolidated())
			throw new IllegalArgumentException("An instruction cannot be modified after consolidation");

		if (meta == null)
			meta = new HashMap<>();

		meta.put(key, value);
	}

	public void unsafePutMeta(String key, Object value) {
		if (meta == null)
			meta = new HashMap<>();

		meta.put(key, value);
	}

	public Object getMeta(String key) {
		if (meta == null)
			return null;

		return meta.get(key);
	}

	public static void transferMeta(RewriterRule.ExplicitLink link) {
		if (link.oldStmt.meta != null)
			link.newStmt.forEach(stmt -> stmt.meta = new HashMap<>(link.oldStmt.meta));
		else
			link.newStmt.forEach(stmt -> stmt.meta = null);
	}

	@Override
	public String toString() {
		return toString(RuleContext.currentContext);
	}
}
