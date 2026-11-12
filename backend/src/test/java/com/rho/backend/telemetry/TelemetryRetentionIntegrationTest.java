package com.rho.backend.telemetry;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the retention delete against real Postgres: expired raw keystrokes go,
 * fresh ones and the session aggregates stay. Needs a database, so it is off by
 * default.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class TelemetryRetentionIntegrationTest {

	private static final int RETENTION_DAYS = 30;

	@Autowired
	private TelemetryRetentionJob job;

	@Autowired
	private DatabaseClient databaseClient;

	@Test
	void removesOnlyExpiredKeystrokesAndKeepsSessionAggregates() {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		try {
			insertUser(userId);
			insertSession(userId, sessionId);

			OffsetDateTime expired = OffsetDateTime.now().minusDays(RETENTION_DAYS + 1L);
			OffsetDateTime fresh = OffsetDateTime.now();
			insertEvent(sessionId, 0, expired);
			insertEvent(sessionId, 1, expired);
			insertEvent(sessionId, 2, fresh);

			long deleted = job.purge(RETENTION_DAYS, 2).block();

			assertThat(deleted).isEqualTo(2);
			assertThat(eventCount(sessionId)).isEqualTo(1);
			assertThat(keystrokeCount(sessionId)).isEqualTo(3);
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

	private void insertSession(UUID userId, UUID sessionId) {
		databaseClient.sql("""
						INSERT INTO sessions (id, user_id, client_session_id, tier, drill_source,
						                      target_length, allowed_charset, status, started_at,
						                      ended_at, keystroke_count, correct_count, accuracy)
						VALUES (:id, :userId, :clientSessionId, 0, 'STATIC',
						        40, 'asdfjkl; ', 'COMPLETED', now(),
						        now(), 3, 3, 100.00)
						""")
				.bind("id", sessionId)
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertEvent(UUID sessionId, int seq, OffsetDateTime createdAt) {
		databaseClient.sql("""
						INSERT INTO keystroke_events (session_id, seq, position, kind, expected_char,
						                              actual_char, is_correct, t_ms, display_ms,
						                              is_warmup, created_at)
						VALUES (:sessionId, :seq, 0, 'KEY', 'a', 'a', true, :t, 100, false, :createdAt)
						""")
				.bind("sessionId", sessionId)
				.bind("seq", seq)
				.bind("t", seq * 100)
				.bind("createdAt", createdAt)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private int eventCount(UUID sessionId) {
		Integer count = databaseClient.sql("SELECT count(*)::int AS total FROM keystroke_events WHERE session_id = :sessionId")
				.bind("sessionId", sessionId)
				.map((row, metadata) -> row.get("total", Integer.class))
				.one()
				.block();
		return count == null ? 0 : count;
	}

	private int keystrokeCount(UUID sessionId) {
		Integer count = databaseClient.sql("SELECT keystroke_count FROM sessions WHERE id = :sessionId")
				.bind("sessionId", sessionId)
				.map((row, metadata) -> row.get("keystroke_count", Integer.class))
				.one()
				.block();
		return count == null ? 0 : count;
	}
}
