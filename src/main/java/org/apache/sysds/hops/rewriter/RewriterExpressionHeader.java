package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.List;

public class RewriterExpressionHeader {
	private RewriterStatement root;
	//private RewriterAssertions assertions;
	private List<RewriterStatement> referencedNodes; // Contains all nodes that are referenced externally (e.g. via meta)

	public RewriterExpressionHeader(RewriterStatement root, final RuleContext ctx) {
		this.root = root;
		//assertions = new RewriterAssertions(ctx);
		referencedNodes = new ArrayList<>();
	}

	public RewriterStatement getExpressionRoot() {
		return root;
	}

	public RewriterAssertions getAssertions() {
		return null;
	}

	public static class SoftReference {
		private int idx;
		public SoftReference(int idx) {
			this.idx = idx;
		}

		public RewriterStatement get(RewriterExpressionHeader header) {
			return header.referencedNodes.get(idx);
		}
	}
}
