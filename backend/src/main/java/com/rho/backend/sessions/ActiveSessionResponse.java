package com.rho.backend.sessions;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A drill a reloaded tab can continue. {@code nextSeq} is one past the highest
 * keystroke already stored, so the client never collides with existing events.
 * {@code position} is the cursor index and {@code elapsedMs} is active time.
 */
public record ActiveSessionResponse(
		UUID sessionId,
		int tier,
		String tierName,
		String text,
		String charset,
		OffsetDateTime startedAt,
		long nextSeq,
		long keystrokes,
		int position,
		long elapsedMs) {
}
