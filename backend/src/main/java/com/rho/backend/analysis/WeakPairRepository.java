package com.rho.backend.analysis;

import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Rolling storage for pair aggregates. The same pair is merged rather than
 * replaced, so recounts add up and a replayed completion cannot hide history.
 */
@Component
public class WeakPairRepository {

	private static final String SELECT = """
			SELECT first_char, second_char, attempts, errors, pauses, total_display_ms
			FROM weak_pairs
			WHERE user_id = :userId AND tier = :tier
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
}
