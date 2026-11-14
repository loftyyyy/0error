package com.rho.backend.analysis;

/**
 * Accumulated behaviour for one ordered pair of expected characters. Only the
 * second key contributes errors and timing: it is the transition into the pair
 * that costs a typist time.
 */
public record WeakPair(
		String first,
		String second,
		int attempts,
		int errors,
		int pauses,
		long totalDisplayMs) {

	/** Timing above this is treated as friction on the transition. */
	public static final int SLOW_GAP_MS = 900;

	private static final double LATENCY_FLOOR_MS = 300.0;
	private static final double LATENCY_RANGE_MS = 1200.0;

	public String pair() {
		return first + second;
	}

	public double errorRate() {
		return attempts == 0 ? 0 : (double) errors / attempts;
	}

	public double pauseRate() {
		return attempts == 0 ? 0 : (double) pauses / attempts;
	}

	public int meanDisplayMs() {
		return attempts == 0 ? 0 : (int) Math.round((double) totalDisplayMs / attempts);
	}

	/**
	 * One bounded weakness score, higher is weaker. Errors matter most, then
	 * visible pauses, then a slow but accurate transition.
	 */
	public double score() {
		double latency = Math.min(1.0,
				Math.max(0.0, (meanDisplayMs() - LATENCY_FLOOR_MS) / LATENCY_RANGE_MS));
		return 0.55 * errorRate() + 0.30 * pauseRate() + 0.15 * latency;
	}
}
