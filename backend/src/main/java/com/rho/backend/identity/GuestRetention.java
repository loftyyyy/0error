package com.rho.backend.identity;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Removes guests that never started a drill. Sign-in writes a row, so a browser
 * opened once should not live in the table forever. Anything with a session
 * keeps its row: the session carries the aggregates, and those are retained
 * indefinitely even after the raw keystrokes are purged.
 */
@Component
public class GuestRetention {

	/** A guest with no session and no activity for a month is not coming back. */
	public static final Duration DORMANT_AFTER = Duration.ofDays(30);

	private static final String DELETE_DORMANT = """
			DELETE FROM users
			WHERE kind = 'GUEST'
			  AND updated_at < :cutoff
			  AND NOT EXISTS (SELECT 1 FROM sessions WHERE sessions.user_id = users.id)
			""";

	private final DatabaseClient databaseClient;

	public GuestRetention(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	public Mono<Long> deleteDormant(Duration olderThan) {
		return databaseClient.sql(DELETE_DORMANT)
				.bind("cutoff", OffsetDateTime.now().minus(olderThan))
				.fetch()
				.rowsUpdated();
	}
}
