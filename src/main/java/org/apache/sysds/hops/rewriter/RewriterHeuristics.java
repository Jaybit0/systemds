package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class RewriterHeuristics implements RewriterHeuristicTransformation {
	List<HeuristicEntry> heuristics = new ArrayList<>();

	public void add(String name, RewriterHeuristicTransformation heur) {
		heuristics.add(new HeuristicEntry(name, heur));
	}

	public void addRepeated(String name, RewriterHeuristicTransformation heur) {
		heuristics.add(new HeuristicEntry(name, new RepeatedHeuristics(heur)));
	}

	@Override
	public RewriterStatement apply(RewriterStatement stmt, @Nullable Function<RewriterStatement, Boolean> func, MutableBoolean bool) {
		for (HeuristicEntry entry : heuristics) {
			System.out.println();
			System.out.println("> " + entry.name + " <");
			System.out.println();

			stmt = entry.heuristics.apply(stmt, func, bool);
		}

		return stmt;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		for (HeuristicEntry entry : heuristics) {
			sb.append("\n> ");
			sb.append(entry.name);
			sb.append(" <\n");

			sb.append(entry.heuristics.toString());
		}

		return sb.toString();
	}

	class RepeatedHeuristics implements RewriterHeuristicTransformation {
		RewriterHeuristicTransformation heuristic;

		public RepeatedHeuristics(RewriterHeuristicTransformation heuristic) {
			this.heuristic = heuristic;
		}

		@Override
		public RewriterStatement apply(RewriterStatement stmt, @Nullable Function<RewriterStatement, Boolean> func, MutableBoolean bool) {
			bool.setValue(true);
			while (bool.getValue()) {
				bool.setValue(false);
				heuristic.apply(stmt, func, bool);
			}

			return stmt;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append("\n===== REPEAT =====\n");

			for (HeuristicEntry entry : heuristics) {
				sb.append("\n> ");
				sb.append(entry.name);
				sb.append(" <\n");

				sb.append(entry.heuristics.toString());
			}

			sb.append("\n===== END REPEAT =====");

			return sb.toString();
		}
	}


	class HeuristicEntry {
		String name;
		RewriterHeuristicTransformation heuristics;

		public HeuristicEntry(String name, RewriterHeuristicTransformation heuristics) {
			this.name = name;
			this.heuristics = heuristics;
		}
	}
}
