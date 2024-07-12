package org.apache.sysds.hops.rewriter;

import java.util.HashMap;

public class RewriterDataType implements RewriterStatement {
	private String id;
	private String type;
	private boolean consolidated = false;

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
	}

	@Override
	public boolean isConsolidated() {
		return consolidated;
	}

	@Override
	public boolean match(RewriterStatement stmt, HashMap<RewriterStatement, RewriterStatement> dependencyMap, HashMap<RewriterStatement, RewriterStatement> links) {
		if (stmt.getResultingDataType().equals(type)) {
			RewriterStatement assoc = dependencyMap.get(this);
			if (assoc == null) {
				dependencyMap.put(this, stmt);
				return true;
			} else if (assoc == stmt) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String toStringWithLinking(HashMap<RewriterStatement, RewriterStatement> links) {
		return toString();
	}

	@Override
	public RewriterStatement clone() {
		return new RewriterDataType().withId(id).ofType(type);
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
