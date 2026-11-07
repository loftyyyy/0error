package com.rho.backend.sessions;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class SessionService {

	private final TypingSessionRepository sessions;
	private final DatabaseClient databaseClient;

	public SessionService(TypingSessionRepository sessions, DatabaseClient databaseClient) {
		this.sessions = sessions;
		this.databaseClient = databaseClient;
	}

	public Mono<CreatedSession> create(UUID userId, CreateSessionRequest request) {
		return sessions.findByUserIdAndClientSessionId(userId, request.clientSessionId())
				.map(existing -> new CreatedSession(existing, false))
				.switchIfEmpty(Mono.defer(() -> insert(userId, request)));
	}

	private Mono<CreatedSession> insert(UUID userId, CreateSessionRequest request) {
		UUID id = UUID.randomUUID();
		String sql = """
				INSERT INTO sessions (
				    id, user_id, client_session_id, tier, drill_source, target_length,
				    allowed_charset, status, started_at, keystroke_count, correct_count, hesitation_count
				)
				VALUES (
				    :id, :userId, :clientSessionId, :tier, :drillSource, :targetLength,
				    :charset, 'ACTIVE', :startedAt, 0, 0, 0
				)
				RETURNING id, client_session_id, tier, drill_source, target_length, allowed_charset,
				          status, started_at
				""";

		return databaseClient.sql(sql)
				.bind("id", id)
				.bind("userId", userId)
				.bind("clientSessionId", request.clientSessionId())
				.bind("tier", request.tier())
				.bind("drillSource", request.drillSource())
				.bind("targetLength", request.targetLength())
				.bind("charset", request.targetCharset())
				.bind("startedAt", OffsetDateTime.now())
				.map((row, metadata) -> new TypingSession(
						row.get("id", UUID.class),
						userId,
						row.get("client_session_id", UUID.class),
						row.get("tier", Integer.class),
						row.get("drill_source", String.class),
						row.get("target_length", Integer.class),
						row.get("allowed_charset", String.class),
						row.get("status", String.class),
						row.get("started_at", OffsetDateTime.class),
						null,
						0,
						0,
						null,
						null,
						null,
						0,
						null,
						null))
				.one()
				.map(session -> new CreatedSession(session, true))
				.onErrorResume(DataIntegrityViolationException.class, error -> sessions
						.findByUserIdAndClientSessionId(userId, request.clientSessionId())
						.map(existing -> new CreatedSession(existing, false))
						.switchIfEmpty(Mono.error(error)));
	}

	/** True when the insert won the race; false when an existing session was reused. */
	public record CreatedSession(TypingSession session, boolean created) {
	}
}
