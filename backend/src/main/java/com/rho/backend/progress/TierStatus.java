package com.rho.backend.progress;

/**
 * One tier as the client sees it, including the effective gate thresholds after
 * any failure relaxation.
 */
public record TierStatus(
		int tier,
		String name,
		String charset,
		boolean unlocked,
		int consecutivePasses,
		int failedAttempts,
		int medianThresholdMs,
		int p95ThresholdMs,
		java.time.OffsetDateTime unlockedAt) {
}
