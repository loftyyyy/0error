package com.rho.backend.progress;

import java.util.UUID;

/**
 * Outcome of applying one completed session to tier progress.
 */
public record GateOutcome(
		TierProgress progress,
		boolean passed,
		Integer unlockedTier,
		TierGateResult gate) {
}
