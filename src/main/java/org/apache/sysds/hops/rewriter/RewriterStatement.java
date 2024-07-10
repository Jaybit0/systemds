package org.apache.sysds.hops.rewriter;

public interface RewriterStatement {
	String getId();
	String getResultingDataType();
	boolean isLiteral();
	void consolidate();
	boolean isConsolidated();
}
