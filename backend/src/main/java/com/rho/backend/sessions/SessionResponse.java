package com.rho.backend.sessions;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionResponse(
		UUID id,
		UUID clientSessionId,
		int tier,
		String targetCharset,
		int targetLength,
		String drillSource,
		String status,
		OffsetDateTime startedAt) {

	public static SessionResponse from(TypingSession session) {
		return new SessionResponse(
				session.id(),
				session.clientSessionId(),
				session.tier(),
				session.allowedCharset(),
				session.targetLength(),
				session.drillSource(),
				session.status(),
				session.startedAt());
	}
}
