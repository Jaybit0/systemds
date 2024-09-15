package org.apache.sysds.hops.rewriter;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class RewriterUtils {
	public static Function<RewriterStatement, Boolean> propertyExtractor(List<String> desiredProperties, final RuleContext ctx) {
		return el -> {
			if (el instanceof RewriterInstruction) {
				Set<String> properties = ((RewriterInstruction) el).getProperties(ctx);
				if (properties != null) {
					for (String desiredProperty : desiredProperties) {
						if (properties.contains(desiredProperty)) {
							String oldInstr = ((RewriterInstruction) el).changeConsolidatedInstruction(desiredProperty, ctx);
							if (el.getMeta("trueInstr") == null)
								el.unsafePutMeta("trueInstr", oldInstr);
						}
					}
				}
			}
			return true;
		};
	}
}
