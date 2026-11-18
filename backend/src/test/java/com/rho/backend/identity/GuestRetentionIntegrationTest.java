package com.rho.backend.identity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dormant-guest sweep against real Postgres: only old guests with no
 * session go, and the foreign keys of everything they may have touched do not
 * stall the delete. Needs a database, so it is off by default.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class GuestRetentionIntegrationTest {

	private static final Duration WINDOW = GuestRetention.DORMANT_AFTER;

	@Autowired
	private GuestRetention retention;

	@Autowired
	private DatabaseClient databaseClient;

	@Test
	void removesOldGuestsThatNeverStartedADrill() {
		UUID dormant = UUID.randomUUID();
		UUID withSession = UUID.randomUUID();
		UUID fresh = UUID.randomUUID();
		UUID registered = UUID.randomUUID();

		try {
			OffsetDateTime old = OffsetDateTime.now().minus(WINDOW).minusDays(1);
			insertGuest(dormant, old);
			// A fetched drill must not pin a guest that never started one.
			insertIssuance(dormant);
			insertGuest(withSession, old);
			insertSession(withSession);
			insertGuest(fresh, OffsetDateTime.now().minusDays(1));
			insertRegistered(registered, old);

			long deleted = retention.deleteDormant(WINDOW).block();

			// The sweep is global, so a shared database may hold other old guests.
			assertThat(deleted).isGreaterThanOrEqualTo(1);
			assertThat(exists(dormant)).isFalse();
			assertThat(exists(withSession)).isTrue();
			assertThat(exists(fresh)).isTrue();
			assertThat(exists(registered)).isTrue();
		}
		finally {
			deleteUser(dormant);
			deleteUser(withSession);
			deleteUser(fresh);
			deleteUser(registered);
		}
	}

	private void insertGuest(UUID id, OffsetDateTime seen) {
		databaseClient.sql("""
						INSERT INTO users (id, kind, guest_token_hash, created_at, updated_at)
						VALUES (:id, 'GUEST', :hash, :seen, :seen)
						""")
				.bind("id", id)
				.bind("hash", UUID.randomUUID().toString().replace("-", ""))
				.bind("seen", seen)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertRegistered(UUID id, OffsetDateTime seen) {
		databaseClient.sql("""
						INSERT INTO users (id, kind, email, created_at, updated_at)
						VALUES (:id, 'REGISTERED', :email, :seen, :seen)
						""")
				.bind("id", id)
				.bind("email", id + "@example.test")
				.bind("seen", seen)
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertSession(UUID userId) {
		databaseClient.sql("""
						INSERT INTO sessions (id, user_id, client_session_id, tier, drill_source,
						                      target_length, allowed_charset, drill_text, status,
						                      started_at)
						VALUES (:id, :userId, :clientSessionId, 0, 'STATIC',
						        43, 'asdfjkl; ', :drillText, 'COMPLETED', :startedAt)
						""")
				.bind("id", UUID.randomUUID())
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
				.bind("drillText", "asdf jkl; asdf jkl; asdf jkl; asdf jkl;")
				.bind("startedAt", OffsetDateTime.now())
				.fetch()
				.rowsUpdated()
				.block();
	}

	private void insertIssuance(UUID userId) {
		databaseClient.sql("""
						INSERT INTO drill_issuances (id, user_id, tier, source, charset,
						                             drill_text, expires_at)
						VALUES (:id, :userId, 0, 'STATIC', 'asdfjkl; ',
						        :drillText, :expiresAt)
						""")
				.bind("id", UUID.randomUUID())
				.bind("userId", userId)
				.bind("drillText", "asdf jkl; asdf jkl; asdf jkl;")
				.bind("expiresAt", OffsetDateTime.now().plusMinutes(10))
				.fetch()
				.rowsUpdated()
				.block();
	}

	private boolean exists(UUID userId) {
		Boolean present = databaseClient.sql(
						"SELECT EXISTS (SELECT 1 FROM users WHERE id = :id) AS present")
				.bind("id", userId)
				.map((row, metadata) -> row.get("present", Boolean.class))
				.one()
				.block();
		return Boolean.TRUE.equals(present);
	}

	private void deleteUser(UUID userId) {
		databaseClient.sql("DELETE FROM users WHERE id = :id")
				.bind("id", userId)
				.fetch()
				.rowsUpdated()
				.block();
	}
}
