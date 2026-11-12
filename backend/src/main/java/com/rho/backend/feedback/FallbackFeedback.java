package com.rho.backend.feedback;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic coaching used whenever the LLM is unavailable. It is the
 * default experience, not an error path, so it must read naturally.
 */
public final class FallbackFeedback {

	private FallbackFeedback() {
	}

	public static List<String> messages(FeedbackContext context) {
		List<String> messages = new ArrayList<>(3);

		messages.add(accuracyMessage(context.accuracy()));
		messages.add(cadenceMessage(context.medianInterKeyMs(), context.p95InterKeyMs()));

		if (context.hesitationCount() == 0) {
			messages.add("No long pauses in this run.");
		}
		else {
			messages.add("You paused %d %s. Ease into the next run and the gap will close."
					.formatted(context.hesitationCount(),
							context.hesitationCount() == 1 ? "time" : "times"));
		}
		return messages;
	}

	/** Short closing line after a stream that failed halfway. */
	public static String closingLine() {
		return "Keep going. The next run is the only one that matters.";
	}

	private static String accuracyMessage(java.math.BigDecimal accuracy) {
		if (accuracy == null) {
			return "Finish a full drill to get an accuracy read.";
		}
		double value = accuracy.doubleValue();
		if (value >= 98.0) {
			return "Your accuracy is already precise.";
		}
		if (value >= 96.0) {
			return "Accuracy is right where it needs to be.";
		}
		return "Slow down a touch; clean keys beat fast keys.";
	}

	private static String cadenceMessage(Integer median, Integer p95) {
		if (median == null) {
			return "There is not enough rhythm data yet.";
		}
		if (median <= 300 && (p95 == null || p95 <= 700)) {
			return "Your hands are moving smoothly.";
		}
		if (median <= 450) {
			return "Your rhythm is steady.";
		}
		return "Leave a little air between keys and let the rhythm settle.";
	}
}
