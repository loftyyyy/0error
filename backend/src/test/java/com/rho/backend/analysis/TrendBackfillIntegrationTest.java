package com.rho.backend.analysis;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A completed session from before the per-session tables existed has no split.
 * The backfill reconstructs it from raw keystrokes while they are still inside
 * the retention window, without touching the rolling aggregates.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class TrendBackfillIntegrationTest {

	@Autowired
	private TrendBackfill backfill;

	@Autowired
	private DatabaseClient databaseClient;

	@Test
	void aMissingSplitIsRebuiltOnceAndNeverMergedIntoTheRollingTotals() {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		UUID purgedId = UUID.randomUUID();

		try {
			insertUser(userId);
			insertCompleted(userId, sessionId, 0);
			insertEvent(sessionId, 0, 0, "d", true);
			insertEvent(sessionId, 1, 1, "k", true);

			// A session whose raw keystrokes were already purged cannot be split.
			insertCompleted(userId, purgedId, 0);

			Long first = backfill.backfill(userId, 30, 100).block();
			assertThat(first).isEqualTo(1L);

			assertThat(count("session_weak_pairs", sessionId)).isEqualTo(1);
			assertThat(count("session_finger_stats", sessionId)).isEqualTo(2);
			assertThat(count("weak_pairs", userId)).isZero();
			assertThat(count("finger_stats", userId)).isZero();

			Long second = backfill.backfill(userId, 30, 100).block();
			assertThat(second).isZero();
			assertThat(count("session_weak_pairs", sessionId)).isEqualTo(1);
		}
		finally {
			deleteUser(userId);
		}
	}

	private long count(String table, UUID id) {
		String column = switch (table) {
			case "session_weak_pairs", "session_finger_stats" -> "session_id";
			default -> "user_id";
		};
		Long value = databaseClient.sql(
						"SELECT count(*) AS total FROM " + table + " WHERE " + column + " = :id")
				.bind("id", id)
				.map((row, metadata) -> row.get("total", Long.class))
				.one()
				.block();
		return value == null ? 0 : value;
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

	private void insertCompleted(UUID userId, UUID sessionId, int tier) {
		databaseClient.sql("""
						INSERT INTO sessions (id, user_id, client_session_id, tier, drill_source,
						                      target_length, allowed_charset, drill_text, status,
						                      started_at, ended_at)
						VALUES (:id, :userId, :clientSessionId, :tier, 'STATIC',
						        40, 'asdfjkl; ', 'asdf jkl; asdf jkl;', 'COMPLETED',
						        now(), now())
						""")
				.bind("id", sessionId)
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
				.bind("tier", tier)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertEvent(UUID sessionId, int seq, int position, String expected, boolean correct) {
		databaseClient.sql("""
						INSERT INTO keystroke_events (session_id, seq, position, kind, expected_char,
						                              actual_char, is_correct, t_ms, display_ms,
						                              is_warmup)
						VALUES (:sessionId, :seq, :position, 'KEY', :expected,
						        :actual, :correct, :t, 100, false)
						""")
				.bind("sessionId", sessionId)
				.bind("seq", seq)
				.bind("position", position)
				.bind("expected", expected)
				.bind("actual", correct ? expected : "x")
				.bind("correct", correct)
				.bind("t", (seq + 1) * 300)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void deleteUser(UUID userId) {
		databaseClient.sql("DELETE FROM users WHERE id = :id")
				.bind("id", userId)
				.fetch()
				.rowsUpdated()
				.block();
	}
}
