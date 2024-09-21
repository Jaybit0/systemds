package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class RewriterDataType extends RewriterStatement {
	private String id;
	private String type;
	private Object literal = null;
	private boolean consolidated = false;
	private int hashCode;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getResultingDataType(final RuleContext ctx) {
		return type;
	}

	@Override
	public boolean isLiteral() {
		return literal != null && !(literal instanceof List<?>);
	}

	@Override
	public Object getLiteral() {
		return literal;
	}

	@Override
	public void setLiteral(Object literal) {
		this.literal = literal;
	}

	@Override
	public boolean isArgumentList() {
		return false;
	}

	@Override
	public List<RewriterStatement> getArgumentList() {
		return null;
	}

	@Override
	public void consolidate(final RuleContext ctx) {
		if (consolidated)
			return;

		if (id == null || id.isEmpty())
			throw new IllegalArgumentException("The id of a data type cannot be empty");
		if (type == null ||type.isEmpty())
			throw new IllegalArgumentException("The type of a data type cannot be empty");

		hashCode = Objects.hash(rid, refCtr, type);
	}

	@Override
	public int recomputeHashCodes(boolean recursively) {
		hashCode = Objects.hash(rid, refCtr, type);
		return hashCode;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean isConsolidated() {
		return consolidated;
	}

	@Override
	public boolean match(final RuleContext ctx, RewriterStatement stmt, HashMap<RewriterStatement, RewriterStatement> dependencyMap, boolean literalsCanBeVariables, boolean ignoreLiteralValue, List<RewriterRule.ExplicitLink> links, final Map<RewriterStatement, RewriterRule.LinkObject> ruleLinks, boolean allowDuplicatePointers, boolean allowPropertyScan) {
		if (stmt.getResultingDataType(ctx).equals(type)) {
			// TODO: This way of literal matching might cause confusion later on
			if (literalsCanBeVariables) {
				if (isLiteral())
					if (!ignoreLiteralValue && (!stmt.isLiteral() || !getLiteral().equals(stmt.getLiteral())))
						return false;
			} else {
				if (isLiteral() != stmt.isLiteral())
					return false;
				if (!ignoreLiteralValue && isLiteral() && !getLiteral().equals(stmt.getLiteral()))
					return false;
			}


			RewriterStatement assoc = dependencyMap.get(this);
			if (assoc == null) {
				if (!allowDuplicatePointers && dependencyMap.containsValue(stmt))
					return false; // Then the statement variable is already associated with another variable
				dependencyMap.put(this, stmt);
				return true;
			} else if (assoc == stmt) {
				return true;
			}
		}
		return false;
	}

	@Override
	public RewriterStatement clone() {
		return new RewriterDataType().as(id).ofType(type);
	}

	@Override
	public RewriterStatement copyNode() {
		return new RewriterDataType().as(id).ofType(type).asLiteral(literal);
	}

	@Override
	public long getCost() {
		return 0;
	}

	@Override
	public RewriterStatement nestedCopyOrInject(Map<RewriterStatement, RewriterStatement> copiedObjects, Function<RewriterStatement, RewriterStatement> injector) {
		RewriterStatement mCpy = copiedObjects.get(this);
		if (mCpy != null)
			return mCpy;
		mCpy = injector.apply(this);
		if (mCpy != null) {
			// Then change the reference to the injected object
			copiedObjects.put(this, mCpy);
			return mCpy;
		}

		RewriterDataType mCopy = new RewriterDataType();
		mCopy.id = id;
		mCopy.type = type;
		if (literal != null && literal instanceof List<?>) {
			final ArrayList<Object> mList = new ArrayList<>(((List<?>)literal).size());
			mCopy.literal = mList;
			((List<?>) literal).forEach(el -> {
				if (el instanceof RewriterStatement)
					mList.add(((RewriterStatement)el).nestedCopyOrInject(copiedObjects, injector));
			});
		} else
			mCopy.literal = literal;
		mCopy.consolidated = consolidated;
		copiedObjects.put(this, mCopy);

		return mCopy;
	}

	@Override
	public RewriterStatement simplify(final RuleContext ctx) {
		return this;
	}

	public String getType() {
		return type;
	}

	@Override
	public RewriterDataType as(String id) {
		if (consolidated)
			throw new IllegalArgumentException("A data type cannot be modified after consolidation");
		this.id = id;
		return this;
	}

	public RewriterDataType ofType(String type) {
		if (consolidated)
			throw new IllegalArgumentException("A data type cannot be modified after consolidation");
		this.type = type;
		return this;
	}

	public RewriterDataType asLiteral(Object literal) {
		if (consolidated)
			throw new IllegalArgumentException("A data type cannot be modified after consolidation");
		this.literal = literal;
		return this;
	}

	@Override
	public String toString(final RuleContext ctx) {
		return isLiteral() ? getLiteral().toString() : getId();
	}
}
