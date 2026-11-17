package com.rho.backend.sessions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.rho.backend.drills.DrillIssuance;
import com.rho.backend.drills.DrillIssuanceStore;
import com.rho.backend.progress.TierProgressService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class SessionService {

	private final TypingSessionRepository sessions;
	private final TierProgressService progress;
	private final DrillIssuanceStore issuances;
	private final DatabaseClient databaseClient;

	public SessionService(
			TypingSessionRepository sessions,
			TierProgressService progress,
			DrillIssuanceStore issuances,
			DatabaseClient databaseClient) {
		this.sessions = sessions;
		this.progress = progress;
		this.issuances = issuances;
		this.databaseClient = databaseClient;
	}

	public Mono<CreatedSession> create(UUID userId, CreateSessionRequest request) {
		// The idempotency key wins before any drill is claimed, so a retried
		// request never burns a second set of issuances.
		return sessions.findByUserIdAndClientSessionId(userId, request.clientSessionId())
				.map(existing -> new CreatedSession(existing, false))
				.switchIfEmpty(Mono.defer(() -> insert(userId, request)));
	}

	private Mono<CreatedSession> insert(UUID userId, CreateSessionRequest request) {
		return issuances.load(userId, request.drillIds())
				.flatMap(claimed -> {
					int tier = claimed.get(0).tier();
					return progress.isUnlocked(userId, tier)
							.flatMap(unlocked -> {
								if (!unlocked) {
									return Mono.error(new ResponseStatusException(
											HttpStatus.FORBIDDEN, "Tier " + tier + " is locked"));
								}
								return issuances.consume(claimed).thenReturn(claimed);
							});
				})
				.flatMap(claimed -> insert(userId, request, claimed));
	}

	private Mono<CreatedSession> insert(
			UUID userId, CreateSessionRequest request, List<DrillIssuance> claimed) {
		DrillIssuance first = claimed.get(0);
		String drillText = claimed.stream()
				.map(DrillIssuance::drillText)
				.collect(Collectors.joining(" "));
		String source = claimed.stream().anyMatch(issuance -> "AI".equals(issuance.source()))
				? "AI"
				: "STATIC";

		UUID id = UUID.randomUUID();
		String sql = """
				INSERT INTO sessions (
				    id, user_id, client_session_id, tier, drill_source, target_length,
				    allowed_charset, drill_text, status, started_at,
				    keystroke_count, correct_count, hesitation_count
				)
				VALUES (
				    :id, :userId, :clientSessionId, :tier, :drillSource, :targetLength,
				    :charset, :drillText, 'ACTIVE', :startedAt, 0, 0, 0
				)
				RETURNING id, client_session_id, tier, drill_source, target_length, allowed_charset,
				          drill_text, status, started_at
				""";

		return databaseClient.sql(sql)
				.bind("id", id)
				.bind("userId", userId)
				.bind("clientSessionId", request.clientSessionId())
				.bind("tier", first.tier())
				.bind("drillSource", source)
				.bind("targetLength", drillText.length())
				.bind("charset", first.charset())
				.bind("drillText", drillText)
				.bind("startedAt", OffsetDateTime.now())
				.map((row, metadata) -> new TypingSession(
						row.get("id", UUID.class),
						userId,
						row.get("client_session_id", UUID.class),
						row.get("tier", Integer.class),
						row.get("drill_source", String.class),
						row.get("target_length", Integer.class),
						row.get("allowed_charset", String.class),
						row.get("drill_text", String.class),
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
