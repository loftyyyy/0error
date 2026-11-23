package com.rho.backend.analysis;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * How one reach has behaved in the guest's recent sessions, oldest first. Each
 * attempt is a completed drill, so the trend shows the session-level mean the
 * typist actually felt rather than a single keystroke.
 */
public record FingerTrend(int tier, String finger, String label, List<Attempt> attempts) {

	public record Attempt(
			OffsetDateTime recordedAt,
			int attempts,
			int errors,
			int pauses,
			int meanMs) {

		static Attempt from(
				OffsetDateTime recordedAt,
				int attempts,
				int errors,
				int pauses,
				long totalDisplayMs) {
			int meanMs = attempts == 0
					? 0
					: (int) Math.round((double) totalDisplayMs / attempts);
			return new Attempt(recordedAt, attempts, errors, pauses, meanMs);
		}
	}
}
