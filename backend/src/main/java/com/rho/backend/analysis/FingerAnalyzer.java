package com.rho.backend.analysis;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.rho.backend.telemetry.KeystrokeEvent;

/**
 * Turns one session's raw keystrokes into per-finger aggregates. Pure, so the
 * reach rules are testable without a database.
 *
 * <p>Every position's first attempt counts once for the finger that owns the
 * expected key; corrections, warmups and backspaces do not add extra attempts.
 * Characters with no finger on a US keyboard are ignored.
 */
public final class FingerAnalyzer {

	private FingerAnalyzer() {
	}

	public static List<FingerStat> analyze(List<KeystrokeEvent> events) {
		Map<Finger, Counts> byFinger = new EnumMap<>(Finger.class);

		for (KeystrokeEvent event : Attempts.firstAttempts(events)) {
			Finger finger = FingerMap.fingerOf(event.expectedChar());
			if (finger != null) {
				byFinger.computeIfAbsent(finger, Counts::new).add(event);
			}
		}

		List<FingerStat> result = byFinger.values().stream()
				.map(Counts::toStat)
				.sorted(Comparator.comparingDouble(FingerStat::score).reversed()
						.thenComparing(FingerStat::finger))
				.toList();
		return result;
	}

	private static final class Counts {

		private final Finger finger;
		private int attempts;
		private int errors;
		private int pauses;
		private long totalDisplayMs;

		private Counts(Finger finger) {
			this.finger = finger;
		}

		private void add(KeystrokeEvent event) {
			attempts += 1;
			if (!event.correct()) {
				errors += 1;
			}
			if (event.displayMs() != null) {
				totalDisplayMs += event.displayMs();
				if (event.displayMs() > Weakness.SLOW_GAP_MS) {
					pauses += 1;
				}
			}
		}

		private FingerStat toStat() {
			return new FingerStat(finger, attempts, errors, pauses, totalDisplayMs);
		}
	}
}
