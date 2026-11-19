package com.rho.backend.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;

/**
 * Reads the guest's finished drills for the result panel. Only completed
 * sessions are listed, scoped to the owner, newest first.
 */
@Service
public class SessionHistoryService {

	public static final int DEFAULT_LIMIT = 5;

	public static final int MAX_LIMIT = 20;

	private static final String RECENT = """
			SELECT id, tier, COALESCE(ended_at, started_at) AS completed_at, accuracy,
			       median_inter_key_ms, p95_inter_key_ms, keystroke_count, gate_passed
			FROM sessions
			WHERE user_id = :userId
			  AND status = 'COMPLETED'
			ORDER BY COALESCE(ended_at, started_at) DESC
			LIMIT :limit
			""";

	private final DatabaseClient databaseClient;
	private final TierCatalogue catalogue;

	public SessionHistoryService(DatabaseClient databaseClient, TierCatalogue catalogue) {
		this.databaseClient = databaseClient;
		this.catalogue = catalogue;
	}

	public Flux<SessionHistoryEntry> recent(UUID userId, int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			return Flux.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT));
		}

		return databaseClient.sql(RECENT)
				.bind("userId", userId)
				.bind("limit", limit)
				.map((row, metadata) -> {
					int tier = row.get("tier", Integer.class);
					return new SessionHistoryEntry(
							row.get("id", UUID.class),
							tier,
							catalogue.require(tier).name(),
							row.get("completed_at", OffsetDateTime.class),
							row.get("accuracy", BigDecimal.class),
							row.get("median_inter_key_ms", Integer.class),
							row.get("p95_inter_key_ms", Integer.class),
							row.get("keystroke_count", Integer.class),
							row.get("gate_passed", Boolean.class));
				})
				.all();
	}
}
