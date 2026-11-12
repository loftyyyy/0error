package com.rho.backend.progress;

import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TierProgressStore {

	private static final String FIND = """
			SELECT tier, consecutive_passes, failed_attempts, unlocked_at
			FROM tier_progress
			WHERE user_id = :userId AND tier = :tier
			""";

	private static final String FIND_ALL = """
			SELECT tier, consecutive_passes, failed_attempts, unlocked_at
			FROM tier_progress
			WHERE user_id = :userId
			""";

	private static final String UPSERT = """
			INSERT INTO tier_progress (user_id, tier, consecutive_passes, failed_attempts, unlocked_at)
			VALUES (:userId, :tier, :passes, :failures, :unlockedAt)
			ON CONFLICT (user_id, tier) DO UPDATE
			SET consecutive_passes = EXCLUDED.consecutive_passes,
			    failed_attempts = EXCLUDED.failed_attempts,
			    unlocked_at = COALESCE(tier_progress.unlocked_at, EXCLUDED.unlocked_at)
			""";

	private static final String UNLOCK = """
			INSERT INTO tier_progress (user_id, tier, consecutive_passes, failed_attempts, unlocked_at)
			VALUES (:userId, :tier, 0, 0, :now)
			ON CONFLICT (user_id, tier) DO UPDATE
			SET unlocked_at = COALESCE(tier_progress.unlocked_at, EXCLUDED.unlocked_at)
			""";

	private final DatabaseClient databaseClient;

	public TierProgressStore(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	public Mono<TierProgress> find(UUID userId, int tier) {
		return databaseClient.sql(FIND)
				.bind("userId", userId)
				.bind("tier", tier)
				.map((row, metadata) -> map(userId, row))
				.one();
	}

	public Flux<TierProgress> findAll(UUID userId) {
		return databaseClient.sql(FIND_ALL)
				.bind("userId", userId)
				.map((row, metadata) -> map(userId, row))
				.all();
	}

	public Mono<Void> save(TierProgress progress) {
		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(UPSERT)
				.bind("userId", progress.userId())
				.bind("tier", progress.tier())
				.bind("passes", progress.consecutivePasses())
				.bind("failures", progress.failedAttempts());

		spec = progress.unlockedAt() == null
				? spec.bindNull("unlockedAt", java.time.OffsetDateTime.class)
				: spec.bind("unlockedAt", progress.unlockedAt());

		return spec.fetch().rowsUpdated().then();
	}

	public Mono<Void> unlock(UUID userId, int tier, java.time.OffsetDateTime now) {
		return databaseClient.sql(UNLOCK)
				.bind("userId", userId)
				.bind("tier", tier)
				.bind("now", now)
				.fetch()
				.rowsUpdated()
				.then();
	}

	private static TierProgress map(UUID userId, io.r2dbc.spi.Row row) {
		return new TierProgress(
				userId,
				row.get("tier", Integer.class),
				row.get("consecutive_passes", Integer.class),
				row.get("failed_attempts", Integer.class),
				row.get("unlocked_at", java.time.OffsetDateTime.class));
	}
}
