package com.rho.backend.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One finished drill as the history list shows it. {@code passed} is null for a
 * session completed before the gate decision was stored.
 */
public record SessionHistoryEntry(
		UUID sessionId,
		int tier,
		String tierName,
		OffsetDateTime completedAt,
		BigDecimal accuracy,
		Integer medianInterKeyMs,
		Integer p95InterKeyMs,
		int keystrokeCount,
		Boolean passed) {
}
