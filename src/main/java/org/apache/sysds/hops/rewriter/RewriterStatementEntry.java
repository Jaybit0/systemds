package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.util.HashMap;

public class RewriterStatementEntry {
	private final RuleContext ctx;
	final RewriterStatement instr;

	public RewriterStatementEntry(final RuleContext ctx, RewriterStatement instr) {
		this.ctx = ctx;
		this.instr = instr;
	}

	@Override
	public int hashCode() {
		return instr.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o.hashCode() != instr.hashCode())
			return false;

		if (o instanceof RewriterStatement)
			return instr.match(new RewriterStatement.MatcherContext(ctx, (RewriterStatement)o, null, -1, false, false, false, false, false, true, new HashMap<>()));
		if (o instanceof RewriterStatementEntry)
			return instr.match(new RewriterStatement.MatcherContext(ctx, ((RewriterStatementEntry)o).instr, null, -1, false, false, false, false, false, true, new HashMap<>()));
		return false;
	}
}