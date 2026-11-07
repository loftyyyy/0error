package com.rho.backend.telemetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives session aggregates from raw keystrokes. Pure and side-effect free so
 * the same rules can be tested without a database.
 *
 * <p>Rules, per the locked spec:
 * <ul>
 *   <li>Warmup keystrokes are stored but never scored.</li>
 *   <li>Accuracy is first-attempt per position; a correction never erases an error.</li>
 *   <li>Cadence is the gap between consecutive scored keypresses.</li>
 *   <li>Gaps over 1500 ms are excluded from cadence and counted as hesitations.</li>
 * </ul>
 */
public final class SessionMetricsCalculator {

	public static final int HESITATION_GAP_MS = 1_500;

	private SessionMetricsCalculator() {
	}

	public static SessionMetrics calculate(List<KeystrokeEvent> events) {
		List<KeystrokeEvent> scored = events.stream()
				.filter(event -> !event.warmup())
				.filter(event -> RawKeystroke.KEY.equals(event.kind()))
				.sorted(Comparator.comparingInt(KeystrokeEvent::seq))
				.toList();

		if (scored.isEmpty()) {
			return new SessionMetrics(0, 0, null, null, null, 0);
		}

		Set<Integer> seenPositions = new HashSet<>();
		int firstAttempts = 0;
		int firstAttemptCorrect = 0;

		for (KeystrokeEvent event : scored) {
			if (seenPositions.add(event.position())) {
				firstAttempts += 1;
				if (event.correct()) {
					firstAttemptCorrect += 1;
				}
			}
		}

		BigDecimal accuracy = BigDecimal.valueOf(firstAttemptCorrect)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(firstAttempts), 2, RoundingMode.HALF_UP);

		List<Integer> intervals = new ArrayList<>();
		int hesitations = 0;
		Integer previousT = null;

		for (KeystrokeEvent event : scored) {
			if (previousT != null) {
				int gap = event.tMs() - previousT;
				if (gap > HESITATION_GAP_MS) {
					hesitations += 1;
				}
				else if (gap >= 0) {
					intervals.add(gap);
				}
			}
			previousT = event.tMs();
		}

		return new SessionMetrics(
				scored.size(),
				firstAttemptCorrect,
				accuracy,
				median(intervals),
				p95(intervals),
				hesitations);
	}

	private static Integer median(List<Integer> values) {
		if (values.isEmpty()) {
			return null;
		}
		List<Integer> sorted = values.stream().sorted().toList();
		int size = sorted.size();
		int middle = size / 2;
		if (size % 2 == 1) {
			return sorted.get(middle);
		}
		long sum = (long) sorted.get(middle - 1) + sorted.get(middle);
		return (int) Math.round(sum / 2.0);
	}

	/** Nearest-rank p95, the value below which 95% of intervals fall. */
	private static Integer p95(List<Integer> values) {
		if (values.isEmpty()) {
			return null;
		}
		List<Integer> sorted = values.stream().sorted().toList();
		int rank = (int) Math.ceil(sorted.size() * 0.95) - 1;
		return sorted.get(Math.max(0, Math.min(rank, sorted.size() - 1)));
	}
}
