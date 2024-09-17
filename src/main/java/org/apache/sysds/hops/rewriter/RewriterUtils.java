package org.apache.sysds.hops.rewriter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class RewriterUtils {
	public static Function<RewriterStatement, Boolean> propertyExtractor(final List<String> desiredProperties, final RuleContext ctx) {
		return el -> {
			if (el instanceof RewriterInstruction) {
				Set<String> properties = ((RewriterInstruction) el).getProperties(ctx);
				String trueInstr = ((RewriterInstruction)el).trueTypedInstruction(ctx);
				if (properties != null) {
					for (String desiredProperty : desiredProperties) {
						if (trueInstr.equals(desiredProperty) || properties.contains(desiredProperty)) {
							System.out.println("Found property: " + desiredProperty + " (for " + el + ")");
							String oldInstr = ((RewriterInstruction) el).changeConsolidatedInstruction(desiredProperty, ctx);
							if (el.getMeta("trueInstr") == null) {
								el.unsafePutMeta("trueInstr", oldInstr);
								el.unsafePutMeta("trueName", oldInstr);
							}
							break;
							//System.out.println("Property found: " + desiredProperty);
						}
					}
				}
			}
			return true;
		};
	}

	public static Function<RewriterStatement, String> binaryStringRepr(String op) {
		return stmt -> {
			List<RewriterStatement> operands = ((RewriterInstruction)stmt).getOperands();
			return operands.get(0).toString() + op + operands.get(1).toString();
		};
	}

	public static void mergeArgLists(RewriterInstruction stmt, final RuleContext ctx) {
		Set<String> ops = new HashSet<>();

		for (Map.Entry<String, HashSet<String>> p : ctx.instrProperties.entrySet()) {
			if (p.getValue().contains("FusedOperator(MATRIX...)")) {
				//String fName = p.getKey().substring(0, p.getKey().indexOf('('));
				ops.add(p.getKey());
			}
		}

		stmt.forEachPostOrderWithDuplicates(RewriterUtils.propertyExtractor(new ArrayList<>(ops), ctx));

		stmt.forEachPostOrderWithDuplicates(el -> {
			if (!(el instanceof RewriterInstruction))
				return true;
			RewriterInstruction e = (RewriterInstruction) el;
			String ts = e.typedInstruction(ctx);

			if (ops.contains(ts) && e.getOperands() != null) {
				for (int idx = 0; idx < e.getOperands().size(); idx++) {
					RewriterStatement stmt2 = e.getOperands().get(idx);
					if (stmt2.isArgumentList()) {
						for (int idx2 = 0; idx2 < stmt2.getOperands().size(); idx2++) {
							RewriterStatement stmt3 = stmt2.getOperands().get(idx2);
							if (stmt3 instanceof RewriterInstruction && ((RewriterInstruction)stmt3).typedInstruction(ctx).equals(ts)) {
								if (stmt3.getOperands() != null && stmt3.getOperands().size() > 0 && stmt3.getOperands().size() == 1) {
									RewriterStatement stmt4 = stmt3.getOperands().get(0);
									if (stmt4.isArgumentList()) {
										stmt2.getOperands().remove(idx);
										stmt2.getOperands().addAll(idx, stmt4.getOperands());
									}
								}

							}
						}
					}
				}
			}

			return true;
		});
	}
}
