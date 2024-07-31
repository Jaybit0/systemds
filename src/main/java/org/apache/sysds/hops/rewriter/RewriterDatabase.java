package org.apache.sysds.hops.rewriter;

import java.util.HashSet;

public class RewriterDatabase {

	private HashSet<RewriterInstructionEntry> db = new HashSet<>();

	public boolean containsEntry(RewriterInstruction instr) {
		return db.contains(instr);
	}

	public boolean insertEntry(RewriterInstruction instr) {
		return db.add(new RewriterInstructionEntry(instr));
	}

	public int size() {return db.size(); }
}
