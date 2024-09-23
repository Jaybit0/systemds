package org.apache.sysds.hops.rewriter;

import org.apache.commons.lang3.mutable.MutableBoolean;

import javax.annotation.Nullable;
import java.util.function.Function;

public interface RewriterHeuristicTransformation {
	RewriterStatement apply(RewriterStatement stmt, @Nullable Function<RewriterStatement, Boolean> func, MutableBoolean bool);

	default RewriterStatement apply(RewriterStatement stmt, @Nullable Function<RewriterStatement, Boolean> func) {
		return apply(stmt, func, new MutableBoolean(false));
	}

	default RewriterStatement apply(RewriterStatement stmt) {
		return apply(stmt, null, new MutableBoolean(false));
	}
}
