package com.rho.backend.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rho.backend.telemetry.KeystrokeEvent;

/**
 * Turns one session's raw keystrokes into ordered-pair aggregates. Pure, so the
 * counting rules are testable without a database.
 *
 * <p>The first attempt at a position defines what happened there: a correction
 * never erases a stumble. Warmup keys and backspaces are ignored, and only
 * consecutive positions form a pair, so a jump across a correction cannot
 * invent a transition that was never typed.
 */
public final class WeakPairAnalyzer {

	private WeakPairAnalyzer() {
	}

	public static List<WeakPair> analyze(List<KeystrokeEvent> events) {
		Map<String, Counts> byPair = new LinkedHashMap<>();
		KeystrokeEvent previous = null;

		for (KeystrokeEvent current : Attempts.firstAttempts(events)) {
			if (previous != null && current.position() == previous.position() + 1) {
				String first = previous.expectedChar();
				String second = current.expectedChar();
				if (Attempts.isPrintableCharacter(first) && Attempts.isPrintableCharacter(second)) {
					Counts counts = byPair.computeIfAbsent(first + second,
							key -> new Counts(first, second));
					counts.add(current);
				}
			}
			previous = current;
		}

		List<WeakPair> result = new ArrayList<>(byPair.size());
		for (Counts counts : byPair.values()) {
			result.add(counts.toPair());
		}
		result.sort(Comparator.comparing(WeakPair::pair));
		return result;
	}

	private static final class Counts {

		private final String first;
		private final String second;
		private int attempts;
		private int errors;
		private int pauses;
		private long totalDisplayMs;

		private Counts(String first, String second) {
			this.first = first;
			this.second = second;
		}

		private void add(KeystrokeEvent secondKey) {
			attempts += 1;
			if (!secondKey.correct()) {
				errors += 1;
			}
			if (secondKey.displayMs() != null) {
				totalDisplayMs += secondKey.displayMs();
				if (secondKey.displayMs() > Weakness.SLOW_GAP_MS) {
					pauses += 1;
				}
			}
		}

		private WeakPair toPair() {
			return new WeakPair(first, second, attempts, errors, pauses, totalDisplayMs);
		}
	}
}
