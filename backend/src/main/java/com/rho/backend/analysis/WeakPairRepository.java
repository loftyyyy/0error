package com.rho.backend.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Rolling storage for pair aggregates plus the per-session copy that makes a
 * trend possible. The same pair is merged rather than replaced in the rolling
 * table, so recounts add up and a replayed completion cannot hide history.
 */
@Component
public class WeakPairRepository {

	private static final String SELECT = """
			SELECT first_char, second_char, attempts, errors, pauses, total_display_ms
			FROM weak_pairs
			WHERE user_id = :userId AND tier = :tier
			""";

	private static final String SELECT_ATTEMPTS = """
			SELECT recorded_at, attempts, errors, pauses, total_display_ms
			FROM session_weak_pairs
			WHERE user_id = :userId AND tier = :tier
			  AND first_char = :first AND second_char = :second
			ORDER BY recorded_at DESC
			LIMIT :limit
			""";

	private final DatabaseClient databaseClient;

	public WeakPairRepository(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	public Mono<Void> merge(UUID userId, int tier, List<WeakPair> pairs) {
		if (pairs.isEmpty()) {
			return Mono.empty();
		}

		StringBuilder sql = new StringBuilder("""
				INSERT INTO weak_pairs
				    (user_id, tier, first_char, second_char, attempts, errors, pauses, total_display_ms)
				VALUES
				""");

		for (int index = 0; index < pairs.size(); index++) {
			if (index > 0) {
				sql.append(",\n");
			}
			sql.append("    (:userId, :tier, :first").append(index)
					.append(", :second").append(index)
					.append(", :attempts").append(index)
					.append(", :errors").append(index)
					.append(", :pauses").append(index)
					.append(", :display").append(index).append(')');
		}

		sql.append("""
				
				ON CONFLICT (user_id, tier, first_char, second_char) DO UPDATE
				SET attempts = weak_pairs.attempts + EXCLUDED.attempts,
				    errors = weak_pairs.errors + EXCLUDED.errors,
				    pauses = weak_pairs.pauses + EXCLUDED.pauses,
				    total_display_ms = weak_pairs.total_display_ms + EXCLUDED.total_display_ms
				""");

		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
				.bind("userId", userId)
				.bind("tier", tier);

		for (int index = 0; index < pairs.size(); index++) {
			WeakPair pair = pairs.get(index);
			spec = spec.bind("first" + index, pair.first())
					.bind("second" + index, pair.second())
					.bind("attempts" + index, pair.attempts())
					.bind("errors" + index, pair.errors())
					.bind("pauses" + index, pair.pauses())
					.bind("display" + index, pair.totalDisplayMs());
		}

		return spec.fetch().rowsUpdated().then();
	}

	/**
	 * Copies this session's pairs, once, for the per-pair trend. A replayed
	 * completion leaves the rows alone instead of counting the attempt twice.
	 */
	public Mono<Void> recordSession(
			UUID sessionId, UUID userId, int tier, List<WeakPair> pairs) {
		if (pairs.isEmpty()) {
			return Mono.empty();
		}

		StringBuilder sql = new StringBuilder("""
				INSERT INTO session_weak_pairs
				    (session_id, user_id, tier, first_char, second_char,
				     attempts, errors, pauses, total_display_ms)
				VALUES
				""");

		for (int index = 0; index < pairs.size(); index++) {
			if (index > 0) {
				sql.append(",\n");
			}
			sql.append("    (:sessionId, :userId, :tier, :first").append(index)
					.append(", :second").append(index)
					.append(", :attempts").append(index)
					.append(", :errors").append(index)
					.append(", :pauses").append(index)
					.append(", :display").append(index).append(')');
		}

		sql.append("""
				
				ON CONFLICT (session_id, first_char, second_char) DO NOTHING
				""");

		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
				.bind("sessionId", sessionId)
				.bind("userId", userId)
				.bind("tier", tier);

		for (int index = 0; index < pairs.size(); index++) {
			WeakPair pair = pairs.get(index);
			spec = spec.bind("first" + index, pair.first())
					.bind("second" + index, pair.second())
					.bind("attempts" + index, pair.attempts())
					.bind("errors" + index, pair.errors())
					.bind("pauses" + index, pair.pauses())
					.bind("display" + index, pair.totalDisplayMs());
		}

		return spec.fetch().rowsUpdated().then();
	}

	public Flux<WeakPair> findByUserAndTier(UUID userId, int tier) {
		return databaseClient.sql(SELECT)
				.bind("userId", userId)
				.bind("tier", tier)
				.map((row, metadata) -> new WeakPair(
						row.get("first_char", String.class),
						row.get("second_char", String.class),
						row.get("attempts", Integer.class),
						row.get("errors", Integer.class),
						row.get("pauses", Integer.class),
						row.get("total_display_ms", Long.class)))
				.all();
	}

	public Flux<WeakPairTrend.Attempt> findRecentAttempts(
			UUID userId, int tier, String first, String second, int limit) {
		return databaseClient.sql(SELECT_ATTEMPTS)
				.bind("userId", userId)
				.bind("tier", tier)
				.bind("first", first)
				.bind("second", second)
				.bind("limit", limit)
				.map((row, metadata) -> WeakPairTrend.Attempt.from(
						row.get("recorded_at", OffsetDateTime.class),
						row.get("attempts", Integer.class),
						row.get("errors", Integer.class),
						row.get("pauses", Integer.class),
						row.get("total_display_ms", Long.class)))
				.all();
	}
}
