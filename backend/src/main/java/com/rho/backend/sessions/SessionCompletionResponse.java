package com.rho.backend.sessions;

import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.SessionMetrics;

/**
 * Server truth for a finished drill. The client renders this; it never computes
 * pass/fail or tier unlocks itself.
 */
public record SessionCompletionResponse(
		UUID sessionId,
		int tier,
		String status,
		SessionMetrics metrics,
		boolean passed,
		int consecutivePasses,
		int failedAttempts,
		Integer unlockedTier,
		int medianThresholdMs,
		int p95ThresholdMs,
		double accuracyThreshold,
		int minimumKeystrokes,
		List<String> failures) {
}
