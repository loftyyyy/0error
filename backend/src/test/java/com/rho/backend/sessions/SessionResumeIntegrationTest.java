package com.rho.backend.sessions;

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
 * A reloaded tab can find the drill it already started and keep its telemetry
 * sequence. The endpoint never exposes another guest's session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class SessionResumeIntegrationTest {

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
	void aGuestFindsTheActiveDrillAndTheNextSequence() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			insertEvent(sessionId, 0);
			insertEvent(sessionId, 1);
			insertEvent(sessionId, 2);

			ActiveSessionResponse active = client.get()
					.uri("/api/v1/sessions/active")
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isOk()
					.expectBody(ActiveSessionResponse.class)
					.returnResult()
					.getResponseBody();

			assertThat(active).isNotNull();
			assertThat(active.sessionId()).isEqualTo(sessionId);
			assertThat(active.tier()).isZero();
			assertThat(active.tierName()).isEqualTo("Home row");
			assertThat(active.text()).isEqualTo("asdf jkl; asdf jkl;");
			assertThat(active.nextSeq()).isEqualTo(3);
			assertThat(active.keystrokes()).isEqualTo(3);
			assertThat(active.position()).isEqualTo(3);
			assertThat(active.elapsedMs()).isEqualTo(900);
			assertThat(active.typedCount()).isEqualTo(3);
			assertThat(active.scoredCount()).isEqualTo(3);
			assertThat(active.correctCount()).isEqualTo(3);
		}
		finally {
			deleteUser(guest.guestId());
		}
	}

	@Test
	void storedFirstAttemptsSeedAccuracyAfterReload() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			insertEvent(sessionId, 0, 0, "KEY", true);
			insertEvent(sessionId, 1, 1, "KEY", false);

			ActiveSessionResponse active = client.get()
					.uri("/api/v1/sessions/active")
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isOk()
					.expectBody(ActiveSessionResponse.class)
					.returnResult()
					.getResponseBody();

			assertThat(active).isNotNull();
			assertThat(active.typedCount()).isEqualTo(2);
			assertThat(active.scoredCount()).isEqualTo(2);
			assertThat(active.correctCount()).isEqualTo(1);
		}
		finally {
			deleteUser(guest.guestId());
		}
	}

	@Test
	void aTrailingBackspaceLeavesTheCursorAtThatIndex() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			insertEvent(sessionId, 0, 0, "KEY");
			insertEvent(sessionId, 1, 1, "KEY");
			insertEvent(sessionId, 2, 1, "BACKSPACE");

			ActiveSessionResponse active = client.get()
					.uri("/api/v1/sessions/active")
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isOk()
					.expectBody(ActiveSessionResponse.class)
					.returnResult()
					.getResponseBody();

			assertThat(active).isNotNull();
			assertThat(active.position()).isEqualTo(1);
			assertThat(active.nextSeq()).isEqualTo(3);
		}
		finally {
			deleteUser(guest.guestId());
		}
	}

	@Test
	void anUntouchedDrillStartsAtSequenceZero() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			ActiveSessionResponse active = client.get()
					.uri("/api/v1/sessions/active")
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isOk()
					.expectBody(ActiveSessionResponse.class)
					.returnResult()
					.getResponseBody();

			assertThat(active).isNotNull();
			assertThat(active.nextSeq()).isZero();
			assertThat(active.keystrokes()).isZero();
			assertThat(active.position()).isZero();
			assertThat(active.elapsedMs()).isZero();
			assertThat(active.typedCount()).isZero();
			assertThat(active.scoredCount()).isZero();
			assertThat(active.correctCount()).isZero();
		}
		finally {
			deleteUser(guest.guestId());
		}
	}

	@Test
	void aCompletedSessionIsNotOfferedForResume() {
		GuestTokenResponse guest = guest();
		UUID sessionId = UUID.randomUUID();
		insertActive(guest.guestId(), sessionId);

		try {
			databaseClient.sql("UPDATE sessions SET status = 'COMPLETED' WHERE id = :id")
					.bind("id", sessionId)
					.fetch()
					.rowsUpdated()
					.block();

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
	void anotherGuestsSessionIsNeverResumed() {
		GuestTokenResponse guest = guest();
		UUID otherUser = UUID.randomUUID();
		insertUser(otherUser);
		UUID otherSession = UUID.randomUUID();
		insertActive(otherUser, otherSession);

		try {
			client.get()
					.uri("/api/v1/sessions/active")
					.headers(headers -> headers.setBearerAuth(guest.token()))
					.exchange()
					.expectStatus().isNoContent();
		}
		finally {
			deleteUser(guest.guestId());
			deleteUser(otherUser);
		}
	}

	@Test
	void theRouteNeedsAToken() {
		client.get()
				.uri("/api/v1/sessions/active")
				.exchange()
				.expectStatus().isUnauthorized();
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

	private void insertEvent(UUID sessionId, int seq) {
		insertEvent(sessionId, seq, seq, "KEY", true);
	}

	private void insertEvent(UUID sessionId, int seq, int position, String kind) {
		insertEvent(sessionId, seq, position, kind, true);
	}

	private void insertEvent(
			UUID sessionId, int seq, int position, String kind, boolean correct) {
		var spec = databaseClient.sql("""
						INSERT INTO keystroke_events (session_id, seq, position, kind, expected_char,
						                              actual_char, is_correct, t_ms, display_ms,
						                              is_warmup)
						VALUES (:sessionId, :seq, :position, :kind, 'a', :actual, :correct,
						        :t, 100, false)
						""")
				.bind("sessionId", sessionId)
				.bind("seq", seq)
				.bind("position", position)
				.bind("kind", kind)
				.bind("correct", correct)
				.bind("t", (seq + 1) * 300);
		spec = "BACKSPACE".equals(kind) || !correct
				? spec.bindNull("actual", String.class)
				: spec.bind("actual", "a");
		spec.fetch().rowsUpdated().block();
	}

	private void deleteUser(UUID userId) {
		databaseClient.sql("DELETE FROM users WHERE id = :id")
				.bind("id", userId)
				.fetch()
				.rowsUpdated()
				.block();
	}
}
