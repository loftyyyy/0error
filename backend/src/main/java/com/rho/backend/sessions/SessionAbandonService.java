package com.rho.backend.sessions;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Closes an open drill on the guest's request. The row stays for history and
 * retention; only its status and honest end time change.
 */
@Service
public class SessionAbandonService {

	private static final String ABANDON = """
			UPDATE sessions
			SET status = 'ABANDONED', ended_at = now()
			WHERE id = :sessionId AND status = 'ACTIVE'
			""";

	private final TypingSessionRepository sessions;
	private final DatabaseClient databaseClient;

	public SessionAbandonService(
			TypingSessionRepository sessions, DatabaseClient databaseClient) {
		this.sessions = sessions;
		this.databaseClient = databaseClient;
	}

	public Mono<Void> abandon(UUID userId, UUID sessionId) {
		return sessions.findByIdAndUserId(sessionId, userId)
				.switchIfEmpty(Mono.error(new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Session not found")))
				.flatMap(session -> {
					if (!"ACTIVE".equals(session.status())) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT,
								"Session is already " + session.status()));
					}
					return databaseClient.sql(ABANDON)
							.bind("sessionId", sessionId)
							.fetch()
							.rowsUpdated()
							.flatMap(updated -> updated == 1
									? Mono.empty()
									: Mono.error(new ResponseStatusException(
											HttpStatus.CONFLICT,
											"Session was closed concurrently")));
				});
	}
}
