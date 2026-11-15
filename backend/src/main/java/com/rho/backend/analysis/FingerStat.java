package com.rho.backend.analysis;

/**
 * Accumulated behaviour for one finger. The finger owns the expected key at a
 * position, so its errors and pauses describe the reach itself.
 */
public record FingerStat(
		Finger finger,
		int attempts,
		int errors,
		int pauses,
		long totalDisplayMs) {

	public double errorRate() {
		return attempts == 0 ? 0 : (double) errors / attempts;
	}

	public double pauseRate() {
		return attempts == 0 ? 0 : (double) pauses / attempts;
	}

	public int meanDisplayMs() {
		return attempts == 0 ? 0 : (int) Math.round((double) totalDisplayMs / attempts);
	}

	/** One bounded weakness score, higher is weaker. */
	public double score() {
		return Weakness.score(attempts, errors, pauses, meanDisplayMs());
	}
}
