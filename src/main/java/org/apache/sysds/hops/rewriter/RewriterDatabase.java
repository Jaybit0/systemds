package org.apache.sysds.hops.rewriter;

import java.util.HashSet;

public class RewriterDatabase {

	private HashSet<RewriterStatementEntry> db = new HashSet<>();

	public boolean containsEntry(RewriterStatement instr) {
		return db.contains(instr);
	}

	public boolean insertEntry(RewriterStatement stmt) {
		return db.add(new RewriterStatementEntry(stmt));
	}

	public int size() {return db.size(); }
}
