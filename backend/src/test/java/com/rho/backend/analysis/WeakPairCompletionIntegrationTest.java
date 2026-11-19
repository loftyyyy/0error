package com.rho.backend.analysis;

import java.util.UUID;

import com.rho.backend.sessions.SessionCompletionResponse;
import com.rho.backend.sessions.SessionCompletionService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the completion transaction records pair aggregates and that a replay
 * returns the stored answer without counting the same session twice. Needs a
 * database, so it is off by default.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class WeakPairCompletionIntegrationTest {

	@Autowired
	private SessionCompletionService completion;

	@Autowired
	private DatabaseClient databaseClient;

	@Test
	void completionMergesPairsOnceAndReplaysTheSameAnswer() {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		try {
			insertUser(userId);
			insertSession(userId, sessionId);
			insertEvent(sessionId, 0, 0, "d", true, 100);
			insertEvent(sessionId, 1, 1, "k", false, 1_000);
			insertEvent(sessionId, 2, 2, "s", true, 100);

			SessionCompletionResponse first = completion.complete(userId, sessionId).block();
			assertThat(first).isNotNull();
			assertPair(userId, "d", "k", 1, 1, 1, 1_000);
			assertPair(userId, "k", "s", 1, 0, 0, 100);
			assertFinger(userId, Finger.LEFT_MIDDLE, 1, 0, 0, 100);
			assertFinger(userId, Finger.RIGHT_MIDDLE, 1, 1, 1, 1_000);
			assertFinger(userId, Finger.LEFT_RING, 1, 0, 0, 100);

			// A retry after a lost response gets the same answer back.
			SessionCompletionResponse replay = completion.complete(userId, sessionId).block();
			assertThat(replay).isEqualTo(first);

			assertPair(userId, "d", "k", 1, 1, 1, 1_000);
			assertFinger(userId, Finger.RIGHT_MIDDLE, 1, 1, 1, 1_000);

			// A row completed before the stored answer existed cannot replay.
			databaseClient.sql("UPDATE sessions SET completion = NULL WHERE id = :id")
					.bind("id", sessionId)
					.fetch()
					.rowsUpdated()
					.block();

			StepVerifier.create(completion.complete(userId, sessionId))
					.expectErrorSatisfies(error -> {
						assertThat(error).isInstanceOf(ResponseStatusException.class);
						assertThat(((ResponseStatusException) error).getStatusCode())
								.isEqualTo(HttpStatus.CONFLICT);
					})
					.verify();
		}
		finally {
			databaseClient.sql("DELETE FROM users WHERE id = :id")
					.bind("id", userId)
					.fetch()
					.rowsUpdated()
					.block();
		}
	}

	private void assertPair(
			UUID userId,
			String first,
			String second,
			int attempts,
			int errors,
			int pauses,
			long totalDisplayMs) {
		WeakPair pair = databaseClient.sql("""
						SELECT attempts, errors, pauses, total_display_ms
						FROM weak_pairs
						WHERE user_id = :userId AND tier = 0
						  AND first_char = :first AND second_char = :second
						""")
				.bind("userId", userId)
				.bind("first", first)
				.bind("second", second)
				.map((row, metadata) -> new WeakPair(
						first,
						second,
						row.get("attempts", Integer.class),
						row.get("errors", Integer.class),
						row.get("pauses", Integer.class),
						row.get("total_display_ms", Long.class)))
				.one()
				.block();

		assertThat(pair).isEqualTo(
				new WeakPair(first, second, attempts, errors, pauses, totalDisplayMs));
	}

	private void assertFinger(
			UUID userId,
			Finger finger,
			int attempts,
			int errors,
			int pauses,
			long totalDisplayMs) {
		FingerStat stat = databaseClient.sql("""
						SELECT attempts, errors, pauses, total_display_ms
						FROM finger_stats
						WHERE user_id = :userId AND tier = 0 AND finger = :finger
						""")
				.bind("userId", userId)
				.bind("finger", finger.name())
				.map((row, metadata) -> new FingerStat(
						finger,
						row.get("attempts", Integer.class),
						row.get("errors", Integer.class),
						row.get("pauses", Integer.class),
						row.get("total_display_ms", Long.class)))
				.one()
				.block();

		assertThat(stat).isEqualTo(
				new FingerStat(finger, attempts, errors, pauses, totalDisplayMs));
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
						                      target_length, allowed_charset, status, started_at)
						VALUES (:id, :userId, :clientSessionId, 0, 'STATIC',
						        40, 'asdfjkl; ', 'ACTIVE', now())
						""")
				.bind("id", sessionId)
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertEvent(
			UUID sessionId, int seq, int position, String expected, boolean correct, int displayMs) {
		databaseClient.sql("""
						INSERT INTO keystroke_events (session_id, seq, position, kind, expected_char,
						                              actual_char, is_correct, t_ms, display_ms,
						                              is_warmup)
						VALUES (:sessionId, :seq, :position, 'KEY', :expected,
						        :actual, :correct, :t, :displayMs, false)
						""")
				.bind("sessionId", sessionId)
				.bind("seq", seq)
				.bind("position", position)
				.bind("expected", expected)
				.bind("actual", correct ? expected : "x")
				.bind("correct", correct)
				.bind("t", (seq + 1) * 300)
				.bind("displayMs", displayMs)
				.fetch()
				.rowsUpdated()
				.block();
	}
}
