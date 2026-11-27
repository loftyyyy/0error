package com.rho.backend.sessions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A drill a reloaded tab can continue. {@code nextSeq} is one past the highest
 * keystroke already stored, so the client never collides with existing events.
 * {@code position} is the cursor index and {@code elapsedMs} is active time.
 * The counts seed the resume display; {@code correctness} holds the last key
 * result per text position, with null for untyped positions.
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
		long elapsedMs,
		int typedCount,
		int scoredCount,
		int correctCount,
		List<Boolean> correctness) {
}
