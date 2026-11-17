package com.rho.backend.sessions;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Proves the abandonment sweep against real Postgres: only stale ACTIVE rows
 * change, their end time stays honest, and completed sessions are untouched.
 * Needs a database, so it is off by default.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class SessionAbandonmentIntegrationTest {

	private static final Duration STALE_AFTER = Duration.ofHours(24);

	@Autowired
	private SessionAbandonment abandonment;

	@Autowired
	private DatabaseClient databaseClient;

	@Test
	void abandonsOnlyActiveSessionsOlderThanACutoff() {
		UUID userId = UUID.randomUUID();
		UUID stale = UUID.randomUUID();
		UUID fresh = UUID.randomUUID();
		UUID completed = UUID.randomUUID();

		try {
			insertUser(userId);
			OffsetDateTime longAgo = OffsetDateTime.now().minusHours(30);
			OffsetDateTime recently = OffsetDateTime.now().minusHours(1);
			insertSession(stale, userId, "ACTIVE", longAgo, longAgo);
			insertSession(fresh, userId, "ACTIVE", recently, recently);
			insertSession(completed, userId, "COMPLETED", longAgo, longAgo);

			long abandoned = abandonment.abandonStale(STALE_AFTER).block();

			// The sweep is global, so a shared database may hold other stale rows.
			// What matters is that this fixture's rows are treated correctly.
			assertThat(abandoned).isGreaterThanOrEqualTo(1);
			assertThat(status(stale)).isEqualTo("ABANDONED");
			// The end time is when the typist stopped, not when the sweep ran.
			assertThat(endedAtOrStart(stale))
					.isCloseTo(longAgo.toInstant(), within(1, ChronoUnit.SECONDS));
			assertThat(hasEndedAt(stale)).isTrue();
			assertThat(status(fresh)).isEqualTo("ACTIVE");
			assertThat(hasEndedAt(fresh)).isFalse();
			assertThat(status(completed)).isEqualTo("COMPLETED");
		}
		finally {
			databaseClient.sql("DELETE FROM users WHERE id = :id")
					.bind("id", userId)
					.fetch()
					.rowsUpdated()
					.block();
		}
	}

	private void insertUser(UUID userId) {
		databaseClient.sql("""
						INSERT INTO users (id, kind, guest_token_hash)
						VALUES (:id, 'GUEST', :hash)
						""")
				.bind("id", userId)
				.bind("hash", UUID.randomUUID().toString().replace("-", ""))
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertSession(
			UUID id, UUID userId, String status, OffsetDateTime startedAt, OffsetDateTime updatedAt) {
		databaseClient.sql("""
						INSERT INTO sessions (id, user_id, client_session_id, tier, drill_source,
						                      target_length, allowed_charset, drill_text, status,
						                      started_at, updated_at)
						VALUES (:id, :userId, :clientSessionId, 0, 'STATIC',
						        43, 'asdfjkl; ', :drillText, :status,
						        :startedAt, :updatedAt)
						""")
				.bind("id", id)
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
				.bind("drillText", "asdf jkl; asdf jkl; asdf jkl; asdf jkl;")
				.bind("status", status)
				.bind("startedAt", startedAt)
				.bind("updatedAt", updatedAt)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private String status(UUID sessionId) {
		return databaseClient.sql("SELECT status FROM sessions WHERE id = :id")
				.bind("id", sessionId)
				.map((row, metadata) -> row.get("status", String.class))
				.one()
				.block();
	}

	/**
	 * The R2DBC Postgres driver refuses to read a NULL timestamptz into a typed
	 * value, so null-ness is asked as a boolean and the value query coalesces.
	 */
	private boolean hasEndedAt(UUID sessionId) {
		Boolean ended = databaseClient.sql(
						"SELECT ended_at IS NOT NULL AS has_ended FROM sessions WHERE id = :id")
				.bind("id", sessionId)
				.map((row, metadata) -> row.get("has_ended", Boolean.class))
				.one()
				.block();
		return Boolean.TRUE.equals(ended);
	}

	private java.time.Instant endedAtOrStart(UUID sessionId) {
		OffsetDateTime ended = databaseClient.sql(
						"SELECT COALESCE(ended_at, started_at) AS ended FROM sessions WHERE id = :id")
				.bind("id", sessionId)
				.map((row, metadata) -> row.get("ended", OffsetDateTime.class))
				.one()
				.block();
		return ended == null ? null : ended.toInstant();
	}
}
