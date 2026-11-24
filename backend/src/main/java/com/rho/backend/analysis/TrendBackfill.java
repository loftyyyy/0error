package com.rho.backend.analysis;

import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;
import com.rho.backend.telemetry.KeystrokeEventRepository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Rebuilds per-session pair and finger rows for completed sessions that predate
 * the trend tables, using raw keystrokes while they are still inside the
 * retention window. Only the per-session copies are written; the rolling
 * aggregates already counted these sessions and must not move again.
 */
@Service
public class TrendBackfill {

	private static final String CANDIDATES = """
			SELECT s.id, s.user_id, s.tier
			FROM sessions s
			WHERE s.status = 'COMPLETED'
			  AND COALESCE(s.ended_at, s.started_at) >= now() - (:days * interval '1 day')
			  AND EXISTS (SELECT 1 FROM keystroke_events e WHERE e.session_id = s.id)
			  AND (
			      NOT EXISTS (SELECT 1 FROM session_weak_pairs p WHERE p.session_id = s.id)
			      OR NOT EXISTS (SELECT 1 FROM session_finger_stats f WHERE f.session_id = s.id)
			  )
			%s
			ORDER BY COALESCE(s.ended_at, s.started_at) DESC
			LIMIT :limit
			""";

	private final DatabaseClient databaseClient;
	private final KeystrokeEventRepository keystrokes;
	private final WeakPairRepository weakPairs;
	private final FingerStatRepository fingers;

	public TrendBackfill(
			DatabaseClient databaseClient,
			KeystrokeEventRepository keystrokes,
			WeakPairRepository weakPairs,
			FingerStatRepository fingers) {
		this.databaseClient = databaseClient;
		this.keystrokes = keystrokes;
		this.weakPairs = weakPairs;
		this.fingers = fingers;
	}

	/** All guests, bounded by the retention window and one batch. */
	public Mono<Long> backfill(int retentionDays, int limit) {
		return backfill(null, retentionDays, limit);
	}

	/** Scoped to one guest when {@code userId} is present; used by tests. */
	Mono<Long> backfill(UUID userId, int retentionDays, int limit) {
		if (retentionDays < 1 || limit < 1) {
			return Mono.just(0L);
		}

		String filter = userId == null ? "" : "AND s.user_id = :userId";
		DatabaseClient.GenericExecuteSpec spec = databaseClient
				.sql(CANDIDATES.formatted(filter))
				.bind("days", retentionDays)
				.bind("limit", limit);
		if (userId != null) {
			spec = spec.bind("userId", userId);
		}

		return spec.map((row, metadata) -> new Candidate(
						row.get("id", UUID.class),
						row.get("user_id", UUID.class),
						row.get("tier", Integer.class)))
				.all()
				.concatMap(this::rebuild)
				.filter(repaired -> repaired)
				.count();
	}

	private Mono<Boolean> rebuild(Candidate candidate) {
		return keystrokes.findBySessionIdOrderBySeqAsc(candidate.sessionId())
				.collectList()
				.flatMap(events -> {
					if (events.isEmpty()) {
						return Mono.just(false);
					}
					List<WeakPair> pairs = WeakPairAnalyzer.analyze(events);
					List<FingerStat> stats = FingerAnalyzer.analyze(events);
					return weakPairs.recordSession(
									candidate.sessionId(), candidate.userId(),
									candidate.tier(), pairs)
							.then(fingers.recordSession(
									candidate.sessionId(), candidate.userId(),
									candidate.tier(), stats))
							.thenReturn(true);
				});
	}

	private record Candidate(UUID sessionId, UUID userId, int tier) {
	}
}
