package org.apache.sysds.hops.rewriter;

import java.util.HashSet;

public class RewriterDatabase {

	private HashSet<RewriterStatementEntry> db = new HashSet<>();

	public boolean containsEntry(RewriterStatement instr) {
		return db.contains(instr);
	}

	public boolean insertEntry(final RuleContext ctx, RewriterStatement stmt) {
		return db.add(new RewriterStatementEntry(ctx, stmt));
	}

	public int size() {return db.size(); }
}
