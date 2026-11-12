package com.rho.backend.feedback;

import java.math.BigDecimal;

/** Performance facts a coach may mention. Nothing raw or personally identifying. */
public record FeedbackContext(
		int tier,
		String tierName,
		int keystrokeCount,
		int correctCount,
		BigDecimal accuracy,
		Integer medianInterKeyMs,
		Integer p95InterKeyMs,
		int hesitationCount) {
}
