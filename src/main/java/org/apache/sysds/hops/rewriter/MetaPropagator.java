package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.NotImplementedException;

import java.util.UUID;
import java.util.function.Consumer;

public class MetaPropagator implements Consumer<RewriterStatement> {
	private final RuleContext ctx;

	public MetaPropagator(RuleContext ctx) {
		this.ctx = ctx;
	}

	// TODO: This must actually return the top element
	public void accept(RewriterStatement root) {
		root.forEachPostOrder((el, parent, pIdx) -> {
			propagateDims(el, parent, pIdx);
		});
	}

	private void propagateDims(RewriterStatement root, RewriterStatement parent, int pIdx) {
		if (!root.getResultingDataType(ctx).startsWith("MATRIX")) {
			if (root.isInstruction()) {
				String ti = root.trueTypedInstruction(ctx);
				switch (ti) {
					case "ncol(MATRIX)":
						parent.getOperands().set(pIdx, (RewriterStatement)root.getOperands().get(0).getMeta("ncol"));
						break;
					case "nrow(MATRIX)":
						parent.getOperands().set(pIdx, (RewriterStatement)root.getOperands().get(0).getMeta("nrow"));
						break;
				}
			}
			return;
		}

		Object colAccess;
		Object rowAccess;

		if (root.getOperands() == null || root.getOperands().isEmpty()) {
			root.unsafePutMeta("ncol", new RewriterInstruction().withInstruction("ncol").withOps(root).as(UUID.randomUUID().toString()).consolidate(ctx));
			root.unsafePutMeta("nrow", new RewriterInstruction().withInstruction("nrow").withOps(root).as(UUID.randomUUID().toString()).consolidate(ctx));
			return;
		}

		if (root.isInstruction()) {
			switch(root.trueInstruction()) {
				// Handle generators
				case "rand":
					root.unsafePutMeta("nrow", root.getOperands().get(0));
					root.unsafePutMeta("ncol", root.getOperands().get(1));
					return;
				case "as.matrix":
					root.unsafePutMeta("ncol", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1));
					root.unsafePutMeta("nrow", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1));
					return;
			}

			switch(root.trueTypedInstruction(ctx)) {
				case "t(MATRIX)":
					colAccess = root.getOperands().get(0).getMeta("ncol");
					rowAccess = root.getOperands().get(0).getMeta("nrow");
					root.unsafePutMeta("ncol", rowAccess);
					root.unsafePutMeta("nrow", colAccess);
					return;
				case "_m(INT,INT,FLOAT)":
					if (root.getOperands().get(0).isInstruction()
							&& root.getOperands().get(0).trueTypedInstruction(ctx).equals("_idx(INT,INT)")) {
						root.unsafePutMeta("nrow", root.getOperands().get(0).getOperands().get(1));
					} else {
						root.unsafePutMeta("nrow", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1).consolidate(ctx));
					}

					if (root.getOperands().get(1).isInstruction()
							&& root.getOperands().get(1).trueTypedInstruction(ctx).equals("_idx(INT,INT)")) {
						root.unsafePutMeta("nrow", root.getOperands().get(1).getOperands().get(1));
					} else {
						root.unsafePutMeta("ncol", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1).consolidate(ctx));
					}
					return;
				case "%*%(MATRIX,MATRIX)":
					rowAccess = root.getOperands().get(0).getMeta("nrow");
					colAccess = root.getOperands().get(1).getMeta("ncol");
					root.unsafePutMeta("nrow", rowAccess);
					root.unsafePutMeta("ncol", colAccess);
					return;
				case "sum(MATRIX)":
					root.unsafePutMeta("nrow", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1));
					root.unsafePutMeta("ncol", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1));
					return;
				case "diag(MATRIX)":
					root.unsafePutMeta("nrow", root.getOperands().get(0).getMeta("nrow"));
					root.unsafePutMeta("ncol", new RewriterDataType().ofType(root.getResultingDataType(ctx)).as("1").asLiteral(1));
					return;
				case "[](MATRIX,INT,INT,INT,INT)":
					Integer[] ints = new Integer[4];

					for (int i = 0; i < 4; i++)
						if (root.getOperands().get(1).isLiteral())
							ints[i] = (Integer)root.getOperands().get(1).getLiteral();

					if (ints[0] != null && ints[1] != null) {
						root.unsafePutMeta("nrow", ints[1] - ints[0] + 1);
					} else {
						throw new NotImplementedException();
						// TODO:
					}

					if (ints[2] != null && ints[3] != null) {
						root.unsafePutMeta("ncol", ints[3] - ints[2] + 1);
					} else {
						throw new NotImplementedException();
					}

					return;
			}

			RewriterInstruction instr = (RewriterInstruction) root;

			if (instr.getProperties(ctx).contains("ElementWiseInstruction")) {
				if (root.getOperands().get(0).getResultingDataType(ctx).equals("MATRIX")) {
					root.unsafePutMeta("nrow", root.getOperands().get(0).getMeta("nrow"));
					root.unsafePutMeta("ncol", root.getOperands().get(0).getMeta("ncol"));
				} else {
					root.unsafePutMeta("nrow", root.getOperands().get(1).getMeta("nrow"));
					root.unsafePutMeta("ncol", root.getOperands().get(1).getMeta("ncol"));
				}

				return;
			}

			throw new NotImplementedException("Unknown instruction: " + instr.trueTypedInstruction(ctx) + "\n" + instr.toString(ctx));
		}
	}
}
