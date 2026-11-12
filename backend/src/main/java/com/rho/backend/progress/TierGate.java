package com.rho.backend.progress;

import java.util.ArrayList;
import java.util.List;

import com.rho.backend.telemetry.SessionMetrics;

/**
 * The locked unlock gate plus the failure relaxation rule.
 *
 * <p>Base gate: accuracy at least 96%, median inter-key at most 450 ms, p95 at
 * most 900 ms, at least 120 scored keystrokes, and two consecutive passes.
 * Every third failed attempt lowers both cadence thresholds by 15%.
 */
public final class TierGate {

	public static final double MIN_ACCURACY = 96.0;
	public static final int BASE_MEDIAN_MS = 450;
	public static final int BASE_P95_MS = 900;
	public static final int MIN_KEYSTROKES = 120;
	public static final int REQUIRED_CONSECUTIVE_PASSES = 2;
	private static final double RELAXATION_FACTOR = 0.85;

	private TierGate() {
	}

	public static TierGateResult evaluate(SessionMetrics metrics, int failedAttempts) {
		int medianThreshold = relax(BASE_MEDIAN_MS, failedAttempts);
		int p95Threshold = relax(BASE_P95_MS, failedAttempts);

		List<String> failures = new ArrayList<>();

		double accuracy = metrics.accuracy() == null ? -1 : metrics.accuracy().doubleValue();
		if (accuracy < MIN_ACCURACY) {
			failures.add("accuracy %.2f < %.0f".formatted(accuracy, MIN_ACCURACY));
		}

		Integer median = metrics.medianInterKeyMs();
		if (median == null || median > medianThreshold) {
			failures.add("median %s > %d".formatted(median, medianThreshold));
		}

		Integer p95 = metrics.p95InterKeyMs();
		if (p95 == null || p95 > p95Threshold) {
			failures.add("p95 %s > %d".formatted(p95, p95Threshold));
		}

		if (metrics.keystrokeCount() < MIN_KEYSTROKES) {
			failures.add("keystrokes %d < %d".formatted(metrics.keystrokeCount(), MIN_KEYSTROKES));
		}

		return new TierGateResult(
				failures.isEmpty(),
				List.copyOf(failures),
				medianThreshold,
				p95Threshold,
				MIN_ACCURACY,
				MIN_KEYSTROKES,
				REQUIRED_CONSECUTIVE_PASSES);
	}

	/** Applies one 15% relaxation for every three failed attempts. */
	public static int relax(int baseThreshold, int failedAttempts) {
		int steps = Math.max(0, failedAttempts) / 3;
		return (int) Math.round(baseThreshold * Math.pow(RELAXATION_FACTOR, steps));
	}
}
