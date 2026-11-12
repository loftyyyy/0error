package com.rho.backend.progress;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Gate state for one user and tier. A null {@code unlockedAt} means locked.
 */
public record TierProgress(
		UUID userId,
		int tier,
		int consecutivePasses,
		int failedAttempts,
		OffsetDateTime unlockedAt) {

	public static TierProgress empty(UUID userId, int tier, OffsetDateTime now) {
		return new TierProgress(userId, tier, 0, 0, tier == 0 ? now : null);
	}

	public boolean unlocked() {
		return unlockedAt != null;
	}
}
