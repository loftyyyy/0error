package com.rho.backend.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Rolling storage for finger aggregates plus the per-session copy that makes a
 * trend possible. The same finger is merged rather than replaced in the rolling
 * table, so recounts add up and a replayed completion cannot hide history.
 */
@Component
public class FingerStatRepository {

	private static final String SELECT = """
			SELECT finger, attempts, errors, pauses, total_display_ms
			FROM finger_stats
			WHERE user_id = :userId AND tier = :tier
			""";

	private static final String SELECT_ATTEMPTS = """
			SELECT recorded_at, attempts, errors, pauses, total_display_ms
			FROM session_finger_stats
			WHERE user_id = :userId AND tier = :tier AND finger = :finger
			ORDER BY recorded_at DESC
			LIMIT :limit
			""";

	private final DatabaseClient databaseClient;

	public FingerStatRepository(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	public Mono<Void> merge(UUID userId, int tier, List<FingerStat> stats) {
		if (stats.isEmpty()) {
			return Mono.empty();
		}

		StringBuilder sql = new StringBuilder("""
				INSERT INTO finger_stats
				    (user_id, tier, finger, attempts, errors, pauses, total_display_ms)
				VALUES
				""");

		for (int index = 0; index < stats.size(); index++) {
			if (index > 0) {
				sql.append(",\n");
			}
			sql.append("    (:userId, :tier, :finger").append(index)
					.append(", :attempts").append(index)
					.append(", :errors").append(index)
					.append(", :pauses").append(index)
					.append(", :display").append(index).append(')');
		}

		sql.append("""
				
				ON CONFLICT (user_id, tier, finger) DO UPDATE
				SET attempts = finger_stats.attempts + EXCLUDED.attempts,
				    errors = finger_stats.errors + EXCLUDED.errors,
				    pauses = finger_stats.pauses + EXCLUDED.pauses,
				    total_display_ms = finger_stats.total_display_ms + EXCLUDED.total_display_ms
				""");

		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
				.bind("userId", userId)
				.bind("tier", tier);

		for (int index = 0; index < stats.size(); index++) {
			FingerStat stat = stats.get(index);
			spec = spec.bind("finger" + index, stat.finger().name())
					.bind("attempts" + index, stat.attempts())
					.bind("errors" + index, stat.errors())
					.bind("pauses" + index, stat.pauses())
					.bind("display" + index, stat.totalDisplayMs());
		}

		return spec.fetch().rowsUpdated().then();
	}

	/**
	 * Copies this session's finger counters, once, for the per-finger trend. A
	 * replayed completion leaves the rows alone instead of counting again.
	 */
	public Mono<Void> recordSession(
			UUID sessionId, UUID userId, int tier, List<FingerStat> stats) {
		if (stats.isEmpty()) {
			return Mono.empty();
		}

		StringBuilder sql = new StringBuilder("""
				INSERT INTO session_finger_stats
				    (session_id, user_id, tier, finger, attempts, errors, pauses,
				     total_display_ms)
				VALUES
				""");

		for (int index = 0; index < stats.size(); index++) {
			if (index > 0) {
				sql.append(",\n");
			}
			sql.append("    (:sessionId, :userId, :tier, :finger").append(index)
					.append(", :attempts").append(index)
					.append(", :errors").append(index)
					.append(", :pauses").append(index)
					.append(", :display").append(index).append(')');
		}

		sql.append("""
				
				ON CONFLICT (session_id, finger) DO NOTHING
				""");

		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
				.bind("sessionId", sessionId)
				.bind("userId", userId)
				.bind("tier", tier);

		for (int index = 0; index < stats.size(); index++) {
			FingerStat stat = stats.get(index);
			spec = spec.bind("finger" + index, stat.finger().name())
					.bind("attempts" + index, stat.attempts())
					.bind("errors" + index, stat.errors())
					.bind("pauses" + index, stat.pauses())
					.bind("display" + index, stat.totalDisplayMs());
		}

		return spec.fetch().rowsUpdated().then();
	}

	public Flux<FingerStat> findByUserAndTier(UUID userId, int tier) {
		return databaseClient.sql(SELECT)
				.bind("userId", userId)
				.bind("tier", tier)
				.map((row, metadata) -> new FingerStat(
						Finger.valueOf(row.get("finger", String.class)),
						row.get("attempts", Integer.class),
						row.get("errors", Integer.class),
						row.get("pauses", Integer.class),
						row.get("total_display_ms", Long.class)))
				.all();
	}

	public Flux<FingerTrend.Attempt> findRecentAttempts(
			UUID userId, int tier, Finger finger, int limit) {
		return databaseClient.sql(SELECT_ATTEMPTS)
				.bind("userId", userId)
				.bind("tier", tier)
				.bind("finger", finger.name())
				.bind("limit", limit)
				.map((row, metadata) -> FingerTrend.Attempt.from(
						row.get("recorded_at", OffsetDateTime.class),
						row.get("attempts", Integer.class),
						row.get("errors", Integer.class),
						row.get("pauses", Integer.class),
						row.get("total_display_ms", Long.class)))
				.all();
	}
}
