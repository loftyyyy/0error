package com.rho.backend.telemetry;

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Deletes expired raw keystrokes in bounded slices. A bounded delete keeps one
 * long transaction from holding locks on a large append-only table.
 */
@Component
public class PostgresKeystrokeRetention implements KeystrokeRetention {

	private static final String DELETE_BATCH = """
			DELETE FROM keystroke_events
			WHERE id IN (
			    SELECT id
			    FROM keystroke_events
			    WHERE created_at < :cutoff
			    ORDER BY created_at
			    LIMIT :limit
			)
			""";

	private final DatabaseClient databaseClient;

	public PostgresKeystrokeRetention(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	@Override
	public Mono<Long> deleteBatch(Instant cutoff, int limit) {
		return databaseClient.sql(DELETE_BATCH)
				.bind("cutoff", cutoff)
				.bind("limit", limit)
				.fetch()
				.rowsUpdated();
	}
}
