package org.apache.sysds.hops.rewriter;

import org.apache.commons.collections4.bidimap.DualHashBidiMap;

public class RewriterInstructionEntry {
	final RewriterInstruction instr;

	public RewriterInstructionEntry(RewriterInstruction instr) {
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
			return instr.match((RewriterStatement)o, new DualHashBidiMap<>());
		if (o instanceof RewriterInstructionEntry)
			return instr.match(((RewriterInstructionEntry)o).instr, new DualHashBidiMap<>());
		return false;
	}
}
