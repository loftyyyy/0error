package com.rho.backend.sessions;

import java.util.Optional;
import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Finds the drill a returning tab can continue. Only the newest ACTIVE session
 * owned by the guest is returned; completed attempts and other guests' rows are
 * invisible.
 */
@Service
public class SessionResumeService {

	private static final String SEQUENCE = """
			SELECT COALESCE(MAX(seq) + 1, 0) AS next_seq,
			       COUNT(*) AS total
			FROM keystroke_events
			WHERE session_id = :sessionId
			""";

	private final TypingSessionRepository sessions;
	private final TierCatalogue catalogue;
	private final DatabaseClient databaseClient;

	public SessionResumeService(
			TypingSessionRepository sessions,
			TierCatalogue catalogue,
			DatabaseClient databaseClient) {
		this.sessions = sessions;
		this.catalogue = catalogue;
		this.databaseClient = databaseClient;
	}

	public Mono<Optional<ActiveSessionResponse>> find(UUID userId) {
		return sessions.findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, "ACTIVE")
				.flatMap(session -> sequence(session.id())
						.map(position -> Optional.of(new ActiveSessionResponse(
								session.id(),
								session.tier(),
								catalogue.require(session.tier()).name(),
								session.drillText(),
								session.allowedCharset(),
								session.startedAt(),
								position.nextSeq(),
								position.total()))))
				.defaultIfEmpty(Optional.empty());
	}

	private Mono<Sequence> sequence(UUID sessionId) {
		return databaseClient.sql(SEQUENCE)
				.bind("sessionId", sessionId)
				.map((row, metadata) -> new Sequence(
						row.get("next_seq", Integer.class),
						row.get("total", Long.class)))
				.one();
	}

	private record Sequence(int nextSeq, long total) {
	}
}
