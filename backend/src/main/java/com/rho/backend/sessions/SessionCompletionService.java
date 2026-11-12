package com.rho.backend.sessions;

import java.util.UUID;

import com.rho.backend.progress.GateOutcome;
import com.rho.backend.progress.TierProgressService;
import com.rho.backend.telemetry.SessionMetrics;
import com.rho.backend.telemetry.SessionMetricsService;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Closes an active session, recomputes aggregates from raw events one last
 * time, then applies the tier gate. Everything happens in one transaction so a
 * pass can never unlock a tier without the session also being completed.
 */
@Service
public class SessionCompletionService {

	private final TypingSessionRepository sessions;
	private final SessionMetricsService metrics;
	private final TierProgressService progress;
	private final DatabaseClient databaseClient;

	public SessionCompletionService(
			TypingSessionRepository sessions,
			SessionMetricsService metrics,
			TierProgressService progress,
			DatabaseClient databaseClient) {
		this.sessions = sessions;
		this.metrics = metrics;
		this.progress = progress;
		this.databaseClient = databaseClient;
	}

	@Transactional
	public Mono<SessionCompletionResponse> complete(UUID userId, UUID sessionId) {
		return sessions.findByIdAndUserId(sessionId, userId)
				.switchIfEmpty(Mono.error(new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Session not found")))
				.flatMap(session -> {
					if (!"ACTIVE".equals(session.status())) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Session is already " + session.status()));
					}

					return metrics.recompute(sessionId)
							.flatMap(aggregates -> progress.applyCompletion(userId, session.tier(), aggregates)
									.flatMap(outcome -> markCompleted(sessionId)
											.thenReturn(toResponse(sessionId, session.tier(), aggregates, outcome))));
				});
	}

	private Mono<Void> markCompleted(UUID sessionId) {
		return databaseClient.sql("""
						UPDATE sessions
						SET status = 'COMPLETED', ended_at = now()
						WHERE id = :sessionId AND status = 'ACTIVE'
						""")
				.bind("sessionId", sessionId)
				.fetch()
				.rowsUpdated()
				.flatMap(updated -> updated == 1
						? Mono.empty()
						: Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Session was completed concurrently")));
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
