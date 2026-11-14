package com.rho.backend.analysis;

import java.util.List;

/** Pair aggregates for one tier, weakest first. */
public record WeakPairReport(int tier, List<Pair> pairs) {

	public record Pair(
			String pair,
			int attempts,
			int errors,
			int pauses,
			int meanMs,
			double score) {

		static Pair from(WeakPair pair) {
			return new Pair(
					pair.pair(),
					pair.attempts(),
					pair.errors(),
					pair.pauses(),
					pair.meanDisplayMs(),
					pair.score());
		}
	}
}
