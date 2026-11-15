package com.rho.backend.analysis;

/**
 * One bounded weakness score shared by pair and finger aggregates: higher is
 * weaker. Errors matter most, then visible pauses, then a slow but accurate
 * movement.
 */
final class Weakness {

	/** Timing above this is treated as friction. */
	static final int SLOW_GAP_MS = 900;

	private static final double LATENCY_FLOOR_MS = 300.0;
	private static final double LATENCY_RANGE_MS = 1200.0;

	private Weakness() {
	}

	static double score(int attempts, int errors, int pauses, int meanDisplayMs) {
		if (attempts == 0) {
			return 0;
		}
		double latency = Math.min(1.0,
				Math.max(0.0, (meanDisplayMs - LATENCY_FLOOR_MS) / LATENCY_RANGE_MS));
		return 0.55 * ((double) errors / attempts)
				+ 0.30 * ((double) pauses / attempts)
				+ 0.15 * latency;
	}
}
