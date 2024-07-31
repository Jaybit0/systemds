package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class RewriterDataType extends RewriterStatement {
	private String id;
	private String type;
	private boolean consolidated = false;
	private int hashCode;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getResultingDataType() {
		return type;
	}

	@Override
	public boolean isLiteral() {
		return true;
	}

	@Override
	public void consolidate() {
		if (consolidated)
			return;

		if (id == null || id.isEmpty())
			throw new IllegalArgumentException("The id of a data type cannot be empty");
		if (type == null ||type.isEmpty())
			throw new IllegalArgumentException("The type of a data type cannot be empty");

		hashCode = Objects.hash(rid, refCtr, type);
	}

	@Override
	public int recomputeHashCodes() {
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
	public boolean match(RewriterStatement stmt, DualHashBidiMap<RewriterStatement, RewriterStatement> dependencyMap) {
		if (stmt.getResultingDataType().equals(type)) {
			RewriterStatement assoc = dependencyMap.get(this);
			if (assoc == null) {
				if (dependencyMap.containsValue(stmt))
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
		return new RewriterDataType().withId(id).ofType(type);
	}

	@Override
	public RewriterStatement copyNode() {
		return new RewriterDataType().withId(id).ofType(type);
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
		mCopy.consolidated = consolidated;
		copiedObjects.put(this, mCopy);
		return mCopy;
	}

	public String getType() {
		return type;
	}

	public RewriterDataType withId(String id) {
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

	public String toString() {
		return getId();
	}
}
