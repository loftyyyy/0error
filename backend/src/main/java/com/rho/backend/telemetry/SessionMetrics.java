package com.rho.backend.telemetry;

import java.math.BigDecimal;

/**
 * Server-computed aggregates for one session. Null cadence values mean the
 * session has no scored interval yet.
 */
public record SessionMetrics(
		int keystrokeCount,
		int correctCount,
		BigDecimal accuracy,
		Integer medianInterKeyMs,
		Integer p95InterKeyMs,
		int hesitationCount) {
}
