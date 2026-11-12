package com.rho.backend.progress;

import java.util.List;

/**
 * Result of applying the unlock gate to one completed session.
 */
public record TierGateResult(
		boolean passed,
		List<String> failures,
		int medianThresholdMs,
		int p95ThresholdMs,
		double accuracyThreshold,
		int minimumKeystrokes,
		int requiredConsecutivePasses) {
}
