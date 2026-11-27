package com.rho.backend.sessions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
			WITH session_events AS (
			    SELECT * FROM keystroke_events WHERE session_id = :sessionId
			),
			first_attempts AS (
			    SELECT DISTINCT ON (position) position, is_correct
			    FROM session_events
			    WHERE kind = 'KEY' AND NOT is_warmup
			    ORDER BY position, seq
			)
			SELECT COALESCE((SELECT MAX(seq) + 1 FROM session_events), 0) AS next_seq,
			       (SELECT COUNT(*) FROM session_events) AS total,
			       COALESCE((
			           SELECT position FROM session_events ORDER BY seq DESC LIMIT 1
			       ), 0) AS last_position,
			       COALESCE((
			           SELECT kind FROM session_events ORDER BY seq DESC LIMIT 1
			       ), 'KEY') AS last_kind,
			       COALESCE((
			           SELECT t_ms FROM session_events ORDER BY seq DESC LIMIT 1
			       ), 0) AS last_t,
			       (SELECT COUNT(*) FROM session_events
			        WHERE kind = 'KEY' AND NOT is_warmup) AS typed_count,
			       (SELECT COUNT(*) FROM first_attempts) AS scored_count,
			       (SELECT COUNT(*) FROM first_attempts
			        WHERE is_correct) AS correct_count
			""";

	private static final String CORRECTNESS = """
			SELECT DISTINCT ON (position) position, is_correct
			FROM keystroke_events
			WHERE session_id = :sessionId AND kind = 'KEY'
			ORDER BY position, seq DESC
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
				.flatMap(session -> Mono.zip(
								sequence(session.id()),
								correctness(session.id(), session.drillText()))
						.map(state -> Optional.of(new ActiveSessionResponse(
								session.id(),
								session.tier(),
								catalogue.require(session.tier()).name(),
								session.drillText(),
								session.allowedCharset(),
								session.startedAt(),
								state.getT1().nextSeq(),
								state.getT1().total(),
								state.getT1().cursor(),
								state.getT1().elapsedMs(),
								state.getT1().typed(),
								state.getT1().scored(),
								state.getT1().correct(),
								state.getT2()))))
				.defaultIfEmpty(Optional.empty());
	}

	private Mono<List<Boolean>> correctness(UUID sessionId, String drillText) {
		int length = drillText == null ? 0 : drillText.length();
		return databaseClient.sql(CORRECTNESS)
				.bind("sessionId", sessionId)
				.map((row, metadata) -> new PositionResult(
						row.get("position", Integer.class),
						row.get("is_correct", Boolean.class)))
				.all()
				.reduce(new ArrayList<Boolean>(emptySlots(length)), (slots, result) -> {
					if (result.position() >= 0 && result.position() < slots.size()) {
						slots.set(result.position(), result.correct());
					}
					return slots;
				})
				.map(Collections::unmodifiableList);
	}

	private static List<Boolean> emptySlots(int length) {
		List<Boolean> slots = new ArrayList<>(length);
		for (int index = 0; index < length; index += 1) {
			slots.add(null);
		}
		return slots;
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
							row.get("last_t", Integer.class),
							row.get("typed_count", Integer.class),
							row.get("scored_count", Integer.class),
							row.get("correct_count", Integer.class));
				})
				.one();
	}

	private record PositionResult(int position, boolean correct) {
	}

	private record Sequence(
			int nextSeq,
			long total,
			int cursor,
			long elapsedMs,
			int typed,
			int scored,
			int correct) {
	}
}
