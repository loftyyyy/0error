package com.rho.backend.analysis;

import java.util.List;

/** Finger aggregates for one tier, weakest reach first. */
public record FingerReport(int tier, List<Entry> fingers) {

	public record Entry(
			String finger,
			String label,
			int attempts,
			int errors,
			int pauses,
			int meanMs,
			double score) {

		static Entry from(FingerStat stat) {
			return new Entry(
					stat.finger().name(),
					stat.finger().label(),
					stat.attempts(),
					stat.errors(),
					stat.pauses(),
					stat.meanDisplayMs(),
					stat.score());
		}
	}
}
