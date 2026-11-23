package com.rho.backend.sessions;

import java.util.UUID;

import com.rho.backend.analysis.FingerStatService;
import com.rho.backend.analysis.WeakPairService;
import com.rho.backend.progress.GateOutcome;
import com.rho.backend.progress.TierProgressService;
import com.rho.backend.telemetry.KeystrokeEventRepository;
import com.rho.backend.telemetry.SessionMetrics;
import com.rho.backend.telemetry.SessionMetricsService;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Closes an active session, recomputes aggregates from raw events one last
 * time, then applies the tier gate. Everything happens in one transaction so a
 * pass can never unlock a tier without the session also being completed.
 *
 * <p>A completion is idempotent: the scored response is stored with the
 * session, so a retry after a lost response replays the same answer instead of
 * scoring the attempt twice or leaving the client stuck on a conflict.
 */
@Service
public class SessionCompletionService {

	private final TypingSessionRepository sessions;
	private final SessionMetricsService metrics;
	private final TierProgressService progress;
	private final WeakPairService weakPairs;
	private final FingerStatService fingers;
	private final KeystrokeEventRepository keystrokes;
	private final DatabaseClient databaseClient;
	private final ObjectMapper objectMapper;

	public SessionCompletionService(
			TypingSessionRepository sessions,
			SessionMetricsService metrics,
			TierProgressService progress,
			WeakPairService weakPairs,
			FingerStatService fingers,
			KeystrokeEventRepository keystrokes,
			DatabaseClient databaseClient,
			ObjectMapper objectMapper) {
		this.sessions = sessions;
		this.metrics = metrics;
		this.progress = progress;
		this.weakPairs = weakPairs;
		this.fingers = fingers;
		this.keystrokes = keystrokes;
		this.databaseClient = databaseClient;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Mono<SessionCompletionResponse> complete(UUID userId, UUID sessionId) {
		return sessions.findByIdAndUserId(sessionId, userId)
				.switchIfEmpty(Mono.error(new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Session not found")))
				.flatMap(session -> "ACTIVE".equals(session.status())
						? score(userId, session)
						: replay(userId, session));
	}

	private Mono<SessionCompletionResponse> score(UUID userId, TypingSession session) {
		UUID sessionId = session.id();
		return keystrokes.findBySessionIdOrderBySeqAsc(sessionId)
				.collectList()
				.flatMap(rawEvents -> metrics.recompute(sessionId, rawEvents)
						.flatMap(aggregates -> progress
								.applyCompletion(userId, session.tier(), aggregates)
								.flatMap(outcome -> {
									SessionCompletionResponse response = toResponse(
											sessionId, session.tier(), aggregates, outcome);
									String stored = objectMapper.writeValueAsString(response);
									return weakPairs.recordSession(
											sessionId, userId, session.tier(), rawEvents)
											.then(fingers.recordSession(
													sessionId, userId, session.tier(), rawEvents))
											.then(markCompleted(
													sessionId, outcome.passed(), stored))
											.thenReturn(response);
								})));
	}

	/**
	 * Returns the answer the first completion produced. A row completed before
	 * the stored answer existed has nothing to replay, so it stays a conflict.
	 */
	private Mono<SessionCompletionResponse> replay(UUID userId, TypingSession session) {
		if (!"COMPLETED".equals(session.status())) {
			return Mono.error(alreadyClosed(session.status()));
		}
		return databaseClient.sql("""
						SELECT COALESCE(completion::text, '') AS completion
						FROM sessions
						WHERE id = :sessionId AND user_id = :userId
						""")
				.bind("sessionId", session.id())
				.bind("userId", userId)
				.map((row, metadata) -> row.get("completion", String.class))
				.one()
				.switchIfEmpty(Mono.error(alreadyClosed(session.status())))
				.flatMap(stored -> stored.isEmpty()
						? Mono.error(alreadyClosed(session.status()))
						: Mono.fromCallable(() -> objectMapper.readValue(
								stored, SessionCompletionResponse.class)));
	}

	private Mono<Void> markCompleted(UUID sessionId, boolean passed, String completion) {
		return databaseClient.sql("""
						UPDATE sessions
						SET status = 'COMPLETED', ended_at = now(), gate_passed = :passed,
						    completion = CAST(:completion AS jsonb)
						WHERE id = :sessionId AND status = 'ACTIVE'
						""")
				.bind("sessionId", sessionId)
				.bind("passed", passed)
				.bind("completion", completion)
				.fetch()
				.rowsUpdated()
				.flatMap(updated -> updated == 1
						? Mono.empty()
						: Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Session was completed concurrently")));
	}

	private static ResponseStatusException alreadyClosed(String status) {
		return new ResponseStatusException(
				HttpStatus.CONFLICT, "Session is already " + status);
	}

	private static SessionCompletionResponse toResponse(
			UUID sessionId, int tier, SessionMetrics aggregates, GateOutcome outcome) {
		return new SessionCompletionResponse(
				sessionId,
				tier,
				"COMPLETED",
				aggregates,
				outcome.passed(),
				outcome.progress().consecutivePasses(),
				outcome.progress().failedAttempts(),
				outcome.unlockedTier(),
				outcome.gate().medianThresholdMs(),
				outcome.gate().p95ThresholdMs(),
				outcome.gate().accuracyThreshold(),
				outcome.gate().minimumKeystrokes(),
				outcome.gate().failures());
	}
}
