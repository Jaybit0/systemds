package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public abstract class RewriterStatement implements Comparable<RewriterStatement> {
	public static final String META_VARNAME = "_varName";


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
		private final RewriterStatement matchRoot;
		private final RewriterInstruction matchParent;
		private final int rootIndex;
		private final HashMap<RewriterStatement, RewriterStatement> assocs;
		private final List<RewriterRule.ExplicitLink> links;

		public MatchingSubexpression(RewriterStatement matchRoot, RewriterInstruction matchParent, int rootIndex, HashMap<RewriterStatement, RewriterStatement> assocs, List<RewriterRule.ExplicitLink> links) {
			this.matchRoot = matchRoot;
			this.matchParent = matchParent;
			this.assocs = assocs;
			this.rootIndex = rootIndex;
			this.links = links;
		}

		public boolean isRootInstruction() {
			return matchParent == null || matchParent == matchRoot;
		}

		public RewriterStatement getMatchRoot() {
			return matchRoot;
		}

		public RewriterInstruction getMatchParent() {
			return matchParent;
		}

		public int getRootIndex() {
			return rootIndex;
		}

		public HashMap<RewriterStatement, RewriterStatement> getAssocs() {
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
	public abstract boolean match(final RuleContext ctx, RewriterStatement stmt, HashMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues, List<RewriterRule.ExplicitLink> links, final Map<RewriterStatement, RewriterRule.LinkObject> ruleLinks, boolean allowDuplicatePointers, boolean allowPropertyScan, boolean allowTypeHierarchy);
	public abstract int recomputeHashCodes(boolean recursively);
	public abstract long getCost();
	public abstract RewriterStatement simplify(final RuleContext ctx);
	public abstract RewriterStatement as(String id);
	public abstract String toString(final RuleContext ctx);
	public abstract boolean isArgumentList();
	public abstract List<RewriterStatement> getArgumentList();
	public abstract boolean isInstruction();
	public abstract String trueInstruction();
	public abstract String trueTypedInstruction(final RuleContext ctx);
	public void prepareDefinitions(final RuleContext ctx, final List<String> strDefs, final Set<String> varDefs) {
		if (getMeta(META_VARNAME) != null)
			return;

		if (getOperands() != null)
			getOperands().forEach(op -> op.prepareDefinitions(ctx, strDefs, varDefs));

		// Check if it is necessary to define variables
		if (refCtr > 1 && this instanceof RewriterInstruction) {
			RewriterInstruction self = ((RewriterInstruction) this);
			Pattern pattern = Pattern.compile("[a-zA-Z0-9_]+");
			String instr = pattern.matcher(self.getInstr()).matches() ? self.getInstr() : "tmp";
			String varName = "var_" + instr + "_";

			int ctr = 1;
			while (varDefs.contains(varName + ctr))
				ctr++;

			strDefs.add(varName + ctr + " = " + toString(ctx));
			varDefs.add(varName + ctr);
			unsafePutMeta(META_VARNAME, varName + ctr);
		}
	}

	public void eraseDefinitions() {
		unsafeRemoveMeta(META_VARNAME);

		if (getOperands() != null)
			getOperands().forEach(RewriterStatement::eraseDefinitions);
	}

	@Nullable
	public List<RewriterStatement> getOperands() {
		return null;
	}

	public int recomputeHashCodes() {
		return recomputeHashCodes(true);
	}
	public boolean matchSubexpr(final RuleContext ctx, RewriterStatement root, RewriterInstruction parent, int rootIndex, List<MatchingSubexpression> matches, HashMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValues, boolean findFirst, List<RewriterRule.ExplicitLink> links, final Map<RewriterStatement, RewriterRule.LinkObject> ruleLinks, boolean allowDuplicatePointers, boolean allowPropertyScan, boolean allowTypeHierarchy, BiFunction<MatchingSubexpression, List<RewriterRule.ExplicitLink>, Boolean> iff) {
		if (dependencyMap == null)
			dependencyMap = new HashMap<>();
		else
			dependencyMap.clear();

		if (links == null)
			links = new ArrayList<>();
		else
			links.clear();

		boolean foundMatch = match(ctx, root, dependencyMap, literalsCanBeVariables, ignoreLiteralValues, links, ruleLinks, allowDuplicatePointers, allowPropertyScan, allowTypeHierarchy);

		if (foundMatch) {
			MatchingSubexpression match = new MatchingSubexpression(root, parent, rootIndex, dependencyMap, links);
			if (iff == null || iff.apply(match, links)) {
				matches.add(match);

				if (findFirst)
					return true;

				dependencyMap = null;
				links = null;
			} else {
				foundMatch = false;
				links.clear();
				dependencyMap.clear();
			}
		}

		int idx = 0;

		if (root.getOperands() != null && root instanceof RewriterInstruction) {
			for (RewriterStatement stmt : root.getOperands()) {
				if (matchSubexpr(ctx, stmt, (RewriterInstruction) root, idx, matches, dependencyMap, literalsCanBeVariables, ignoreLiteralValues, findFirst, links, ruleLinks, allowDuplicatePointers, allowPropertyScan, allowTypeHierarchy, iff)) {
					dependencyMap = new HashMap<>();
					links = new ArrayList<>();
					foundMatch = true;

					if (findFirst)
						return true;
				}
				idx++;
			}
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
		if (isArgumentList())
			return;
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
		if (rid != 0 || isArgumentList())
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
	 * @param function test
	 */
	public void forEachPostOrderWithDuplicates(Function<RewriterStatement, Boolean> function) {
		if (function.apply(this) && getOperands() != null)
			for (int i = 0; i < getOperands().size(); i++)
				getOperands().get(i).forEachPostOrderWithDuplicates(function);
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

	public void unsafeRemoveMeta(String key) {
		if (meta == null)
			return;

		meta.remove(key);
	}

	public Object getMeta(String key) {
		if (meta == null)
			return null;

		return meta.get(key);
	}

	public static void transferMeta(RewriterRule.ExplicitLink link) {
		if (link.oldStmt instanceof RewriterInstruction) {
			for (RewriterStatement mNew : link.newStmt) {
				if (mNew instanceof RewriterInstruction &&
						!((RewriterInstruction)mNew).trueInstruction().equals(((RewriterInstruction)link.oldStmt).trueInstruction())) {
					((RewriterInstruction) mNew).unsafeSetInstructionName(((RewriterInstruction)link.oldStmt).trueInstruction());
				}
			}
		}

		if (link.oldStmt.meta != null)
			link.newStmt.forEach(stmt -> stmt.meta = new HashMap<>(link.oldStmt.meta));
		else
			link.newStmt.forEach(stmt -> stmt.meta = null);
	}

	@Override
	public String toString() {
		return toString(RuleContext.currentContext);
	}

	public List<String> toExecutableString(final RuleContext ctx) {
		ArrayList<String> defList = new ArrayList<>();
		prepareDefinitions(ctx, defList, new HashSet<>());
		defList.add(toString(ctx));
		eraseDefinitions();

		return defList;
	}
}
