package com.rho.backend.sessions;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Closes sessions a guest walked away from. Until this runs, "ACTIVE" would mean
 * "created at some point", which makes the status useless and keeps the partial
 * active index larger than it needs to be.
 *
 * <p>{@code ended_at} is set to the last update rather than to the sweep time:
 * the honest answer is when the typist stopped, not when the server noticed.</p>
 */
@Component
public class SessionAbandonment {

	/** A drill that has been untouched for a day is not coming back. */
	public static final Duration STALE_AFTER = Duration.ofHours(24);

	private static final String ABANDON_STALE = """
			UPDATE sessions
			SET status = 'ABANDONED',
			    ended_at = COALESCE(ended_at, GREATEST(updated_at, started_at))
			WHERE status = 'ACTIVE'
			  AND updated_at < :cutoff
			""";

	private final DatabaseClient databaseClient;

	public SessionAbandonment(DatabaseClient databaseClient) {
		this.databaseClient = databaseClient;
	}

	public Mono<Long> abandonStale(Duration olderThan) {
		return databaseClient.sql(ABANDON_STALE)
				.bind("cutoff", OffsetDateTime.now().minus(olderThan))
				.fetch()
				.rowsUpdated();
	}
}
