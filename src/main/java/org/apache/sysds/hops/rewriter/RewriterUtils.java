package org.apache.sysds.hops.rewriter;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class RewriterUtils {
	public static Function<RewriterStatement, Boolean> propertyExtractor(final List<String> desiredProperties, final RuleContext ctx) {
		return el -> {
			if (el instanceof RewriterInstruction) {
				Set<String> properties = ((RewriterInstruction) el).getProperties(ctx);
				//System.out.println(el + " => " + properties);
				if (properties != null) {
					for (String desiredProperty : desiredProperties) {
						if (properties.contains(desiredProperty)) {
							//System.out.println("Found property: " + desiredProperty + " (for " + el + ")");
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
}
