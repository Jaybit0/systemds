package org.apache.sysds.hops.rewriter;

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
}
