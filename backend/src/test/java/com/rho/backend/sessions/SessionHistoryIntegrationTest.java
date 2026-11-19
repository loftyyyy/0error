package com.rho.backend.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
 * Proves the history query against real Postgres: only the owner's completed
 * rows, newest first, with null metrics and a null gate decision read as null
 * rather than as a failure. Needs a database, so it is off by default.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class SessionHistoryIntegrationTest {

	@Autowired
	private SessionHistoryService history;

	@Autowired
	private DatabaseClient databaseClient;

	@Test
	void listsOnlyTheGuestsCompletedSessionsNewestFirst() {
		UUID owner = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();
		UUID older = UUID.randomUUID();
		UUID legacy = UUID.randomUUID();
		UUID newer = UUID.randomUUID();
		UUID active = UUID.randomUUID();
		UUID foreign = UUID.randomUUID();

		try {
			insertUser(owner);
			insertUser(stranger);
			OffsetDateTime now = OffsetDateTime.now();

			insertCompleted(older, owner, 1, now.minusDays(3),
					new BigDecimal("97.50"), true);
			insertLegacyCompleted(legacy, owner, 0, now.minusDays(2));
			insertCompleted(newer, owner, 2, now.minusDays(1),
					new BigDecimal("98.00"), false);
			insertActive(active, owner, 0, now);
			insertCompleted(foreign, stranger, 3, now.minusHours(1),
					new BigDecimal("99.00"), true);

			List<SessionHistoryEntry> entries = history.recent(owner, 10)
					.collectList()
					.block();

			assertThat(entries).extracting(SessionHistoryEntry::sessionId)
					.containsExactly(newer, legacy, older);
			assertThat(entries).extracting(SessionHistoryEntry::tierName)
					.containsExactly("Bottom row", "Home row", "Top row");

			assertThat(entries.get(0).passed()).isFalse();
			assertThat(entries.get(2).passed()).isTrue();
			assertThat(entries.get(2).accuracy()).isEqualByComparingTo("97.50");
			assertThat(entries.get(2).keystrokeCount()).isEqualTo(120);

			// A row completed before gate outcomes were stored is unknown, not failed.
			assertThat(entries.get(1).passed()).isNull();
			assertThat(entries.get(1).accuracy()).isNull();
			assertThat(entries.get(1).medianInterKeyMs()).isNull();
			assertThat(entries.get(1).p95InterKeyMs()).isNull();
		}
		finally {
			deleteUser(owner);
			deleteUser(stranger);
		}
	}

	@Test
	void respectsTheLimitAndRefusesLimitsOutsideTheRange() {
		UUID owner = UUID.randomUUID();

		try {
			insertUser(owner);
			OffsetDateTime now = OffsetDateTime.now();
			insertCompleted(UUID.randomUUID(), owner, 0, now.minusMinutes(2),
					new BigDecimal("96.00"), true);
			insertCompleted(UUID.randomUUID(), owner, 0, now.minusMinutes(1),
					new BigDecimal("97.00"), true);

			assertThat(history.recent(owner, 1).collectList().block()).hasSize(1);

			StepVerifier.create(history.recent(owner, 0))
					.expectErrorSatisfies(error -> {
						assertThat(error).isInstanceOf(ResponseStatusException.class);
						assertThat(((ResponseStatusException) error).getStatusCode())
								.isEqualTo(HttpStatus.BAD_REQUEST);
					})
					.verify();

			StepVerifier.create(history.recent(owner, SessionHistoryService.MAX_LIMIT + 1))
					.expectError(ResponseStatusException.class)
					.verify();
		}
		finally {
			deleteUser(owner);
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

	private void insertCompleted(
			UUID id, UUID userId, int tier, OffsetDateTime endedAt, BigDecimal accuracy, boolean passed) {
		insertBase(id, userId, tier, endedAt);
		databaseClient.sql("""
						UPDATE sessions
						SET status = 'COMPLETED',
						    ended_at = :endedAt,
						    keystroke_count = 120,
						    correct_count = 118,
						    accuracy = :accuracy,
						    median_inter_key_ms = 400,
						    p95_inter_key_ms = 800,
						    hesitation_count = 1,
						    gate_passed = :passed
						WHERE id = :id
						""")
				.bind("id", id)
				.bind("endedAt", endedAt)
				.bind("accuracy", accuracy)
				.bind("passed", passed)
				.fetch()
				.rowsUpdated()
				.block();
	}

	/** A completed row from before gate_passed existed: every metric is null. */
	private void insertLegacyCompleted(UUID id, UUID userId, int tier, OffsetDateTime endedAt) {
		insertBase(id, userId, tier, endedAt);
		databaseClient.sql("""
						UPDATE sessions
						SET status = 'COMPLETED', ended_at = :endedAt
						WHERE id = :id
						""")
				.bind("id", id)
				.bind("endedAt", endedAt)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertActive(UUID id, UUID userId, int tier, OffsetDateTime startedAt) {
		insertBase(id, userId, tier, startedAt);
	}

	private void insertBase(UUID id, UUID userId, int tier, OffsetDateTime startedAt) {
		databaseClient.sql("""
						INSERT INTO sessions (id, user_id, client_session_id, tier, drill_source,
						                      target_length, allowed_charset, drill_text, status,
						                      started_at)
						VALUES (:id, :userId, :clientSessionId, :tier, 'STATIC',
						        43, 'asdfjkl; ', :drillText, 'ACTIVE',
						        :startedAt)
						""")
				.bind("id", id)
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
				.bind("tier", tier)
				.bind("drillText", "asdf jkl; asdf jkl; asdf jkl; asdf jkl;")
				.bind("startedAt", startedAt)
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
