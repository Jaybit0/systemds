package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

public class RewriterStatementEntry {
	final RewriterStatement instr;

	public RewriterStatementEntry(RewriterStatement instr) {
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
			return instr.match((RewriterStatement)o, new DualHashBidiMap<>(), false, false);
		if (o instanceof RewriterStatementEntry)
			return instr.match(((RewriterStatementEntry)o).instr, new DualHashBidiMap<>(), false, false);
		return false;
	}
}
