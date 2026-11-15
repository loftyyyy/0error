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
	public static final int SLOW_GAP_MS = Weakness.SLOW_GAP_MS;

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
		return Weakness.score(attempts, errors, pauses, meanDisplayMs());
	}
}
