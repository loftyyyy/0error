package com.rho.backend.sessions;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.rho.backend.identity.GuestTokenResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closing an open drill on purpose. The resume endpoint must stop offering it,
 * but the row and its honest end time stay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class SessionAbandonIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private DatabaseClient databaseClient;

	private WebTestClient client;

	@BeforeEach
	void bindClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@Test
	void anOpenDrillCanBeAbandonedAndStopsResuming() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			client.post()
					.uri("/api/v1/sessions/{id}/abandon", sessionId)
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isNoContent();

			assertThat(status(sessionId)).isEqualTo("ABANDONED");
			assertThat(endedAt(sessionId)).isNotNull();

			client.get()
					.uri("/api/v1/sessions/active")
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isNoContent();
		}
		finally {
			deleteUser(guest.guestId());
		}
	}

	@Test
	void anotherGuestsDrillCannotBeAbandoned() {
		GuestTokenResponse guest = guest();
		UUID otherUser = UUID.randomUUID();
		insertUser(otherUser);
		UUID otherSession = UUID.randomUUID();
		insertActive(otherUser, otherSession);

		try {
			client.post()
					.uri("/api/v1/sessions/{id}/abandon", otherSession)
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isNotFound();

			assertThat(status(otherSession)).isEqualTo("ACTIVE");
		}
		finally {
			deleteUser(guest.guestId());
			deleteUser(otherUser);
		}
	}

	@Test
	void aCompletedDrillIsNotAbandonedAgain() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			databaseClient.sql("""
							UPDATE sessions SET status = 'COMPLETED', ended_at = now()
							WHERE id = :id
							""")
					.bind("id", sessionId)
					.fetch()
					.rowsUpdated()
					.block();

			client.post()
					.uri("/api/v1/sessions/{id}/abandon", sessionId)
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isEqualTo(409);

			assertThat(status(sessionId)).isEqualTo("COMPLETED");
		}
		finally {
			deleteUser(guest.guestId());
		}
	}

	@Test
	void theRouteNeedsAToken() {
		client.post()
				.uri("/api/v1/sessions/{id}/abandon", UUID.randomUUID())
				.exchange()
				.expectStatus().isUnauthorized();
	}

	private String status(UUID sessionId) {
		return databaseClient.sql("SELECT status FROM sessions WHERE id = :id")
				.bind("id", sessionId)
				.map((row, metadata) -> row.get("status", String.class))
				.one()
				.block();
	}

	private OffsetDateTime endedAt(UUID sessionId) {
		return databaseClient.sql("SELECT ended_at FROM sessions WHERE id = :id")
				.bind("id", sessionId)
				.map((row, metadata) -> row.get("ended_at", OffsetDateTime.class))
				.one()
				.block();
	}

	private GuestTokenResponse guest() {
		GuestTokenResponse guest = client.post()
				.uri("/api/v1/auth/guest")
				.exchange()
				.expectStatus().isOk()
				.expectBody(GuestTokenResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(guest).isNotNull();
		return guest;
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

	private void insertActive(UUID userId, UUID sessionId) {
		databaseClient.sql("""
						INSERT INTO sessions (id, user_id, client_session_id, tier, drill_source,
						                      target_length, allowed_charset, drill_text, status,
						                      started_at)
						VALUES (:id, :userId, :clientSessionId, 0, 'STATIC',
						        40, 'asdfjkl; ', 'asdf jkl; asdf jkl;', 'ACTIVE', now())
						""")
				.bind("id", sessionId)
				.bind("userId", userId)
				.bind("clientSessionId", UUID.randomUUID())
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
