package com.rho.backend.feedback;

import java.math.BigDecimal;
import java.util.List;

/** Performance facts a coach may mention. Nothing raw or personally identifying. */
public record FeedbackContext(
		int tier,
		String tierName,
		int keystrokeCount,
		int correctCount,
		BigDecimal accuracy,
		Integer medianInterKeyMs,
		Integer p95InterKeyMs,
		int hesitationCount,
		List<String> focusPairs,
		String weakReach) {
}
