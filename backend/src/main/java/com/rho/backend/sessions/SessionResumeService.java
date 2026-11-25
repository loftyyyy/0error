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
			       COUNT(*) AS total,
			       COALESCE((
			           SELECT position FROM keystroke_events
			           WHERE session_id = :sessionId ORDER BY seq DESC LIMIT 1
			       ), 0) AS last_position,
			       COALESCE((
			           SELECT kind FROM keystroke_events
			           WHERE session_id = :sessionId ORDER BY seq DESC LIMIT 1
			       ), 'KEY') AS last_kind,
			       COALESCE((
			           SELECT t_ms FROM keystroke_events
			           WHERE session_id = :sessionId ORDER BY seq DESC LIMIT 1
			       ), 0) AS last_t
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
								position.total(),
								position.cursor(),
								position.elapsedMs()))))
				.defaultIfEmpty(Optional.empty());
	}

	private Mono<Sequence> sequence(UUID sessionId) {
		return databaseClient.sql(SEQUENCE)
				.bind("sessionId", sessionId)
				.map((row, metadata) -> {
					long total = row.get("total", Long.class);
					int lastPosition = row.get("last_position", Integer.class);
					String lastKind = row.get("last_kind", String.class);
					int cursor = total == 0
							? 0
							: ("KEY".equals(lastKind) ? lastPosition + 1 : lastPosition);
					return new Sequence(
							row.get("next_seq", Integer.class),
							total,
							cursor,
							row.get("last_t", Integer.class));
				})
				.one();
	}

	private record Sequence(int nextSeq, long total, int cursor, long elapsedMs) {
	}
}
