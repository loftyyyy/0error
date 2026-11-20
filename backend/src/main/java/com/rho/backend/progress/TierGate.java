package com.rho.backend.progress;

import java.math.BigDecimal;
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

		BigDecimal accuracy = metrics.accuracy();
		if (accuracy == null) {
			failures.add("Accuracy not measured: needs %.0f%% or more.".formatted(MIN_ACCURACY));
		}
		else if (accuracy.doubleValue() < MIN_ACCURACY) {
			failures.add("Accuracy %.2f%%: needs %.0f%% or more."
					.formatted(accuracy.doubleValue(), MIN_ACCURACY));
		}

		Integer median = metrics.medianInterKeyMs();
		if (median == null) {
			failures.add("Median not measured: needs %d ms or less.".formatted(medianThreshold));
		}
		else if (median > medianThreshold) {
			failures.add("Median %d ms: needs %d ms or less."
					.formatted(median, medianThreshold));
		}

		Integer p95 = metrics.p95InterKeyMs();
		if (p95 == null) {
			failures.add("p95 not measured: needs %d ms or less.".formatted(p95Threshold));
		}
		else if (p95 > p95Threshold) {
			failures.add("p95 %d ms: needs %d ms or less.".formatted(p95, p95Threshold));
		}

		if (metrics.keystrokeCount() < MIN_KEYSTROKES) {
			failures.add("Keystrokes %d: needs %d or more."
					.formatted(metrics.keystrokeCount(), MIN_KEYSTROKES));
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
