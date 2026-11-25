package com.rho.backend.sessions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;
import com.rho.backend.telemetry.SessionMetrics;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final SessionService sessions = mock(SessionService.class);
	private final SessionCompletionService completion = mock(SessionCompletionService.class);
	private final SessionHistoryService history = mock(SessionHistoryService.class);
	private final SessionResumeService resume = mock(SessionResumeService.class);
	private final SessionAbandonService abandonment = mock(SessionAbandonService.class);
	private final SessionController controller =
			new SessionController(sessions, completion, history, resume, abandonment);

	@Test
	void everyRouteRejectsAGuestWithoutAToken() {
		CreateSessionRequest request =
				new CreateSessionRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));

		StepVerifier.create(controller.create(request, null))
				.expectError(ResponseStatusException.class).verify();
		StepVerifier.create(controller.recent(5, null))
				.expectError(ResponseStatusException.class).verify();
		StepVerifier.create(controller.complete(SESSION_ID, null))
				.expectError(ResponseStatusException.class).verify();
		StepVerifier.create(controller.active(null))
				.expectError(ResponseStatusException.class).verify();
		StepVerifier.create(controller.abandon(SESSION_ID, null))
				.expectError(ResponseStatusException.class).verify();

		verifyNoInteractions(sessions, completion, history, resume, abandonment);
	}

	@Test
	void abandonForwardsTheOwnedSession() {
		when(abandonment.abandon(USER_ID, SESSION_ID)).thenReturn(Mono.empty());

		ResponseEntity<Void> response = controller.abandon(SESSION_ID, PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		verify(abandonment).abandon(USER_ID, SESSION_ID);
	}

	@Test
	void anOpenDrillIsReturnedForResume() {
		ActiveSessionResponse active = new ActiveSessionResponse(
				SESSION_ID, 0, "Home row", "asdf jkl;", "asdfjkl; ",
				OffsetDateTime.now(), 4, 3, 3, 900, 3, 3, 3);
		when(resume.find(USER_ID)).thenReturn(Mono.just(java.util.Optional.of(active)));

		ResponseEntity<ActiveSessionResponse> response =
				controller.active(PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(active);
	}

	@Test
	void noOpenDrillAnswers204() {
		when(resume.find(USER_ID)).thenReturn(Mono.just(java.util.Optional.empty()));

		ResponseEntity<ActiveSessionResponse> response =
				controller.active(PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void aNewSessionIsCreatedWith201() {
		CreateSessionRequest request =
				new CreateSessionRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));
		TypingSession session = session();
		when(sessions.create(USER_ID, request))
				.thenReturn(Mono.just(new SessionService.CreatedSession(session, true)));

		ResponseEntity<SessionResponse> response =
				controller.create(request, PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().id()).isEqualTo(SESSION_ID);
	}

	@Test
	void aRepeatedCreateIsIdempotentWith200() {
		CreateSessionRequest request =
				new CreateSessionRequest(UUID.randomUUID(), List.of(UUID.randomUUID()));
		when(sessions.create(USER_ID, request))
				.thenReturn(Mono.just(new SessionService.CreatedSession(session(), false)));

		ResponseEntity<SessionResponse> response =
				controller.create(request, PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void recentForwardsTheWindowAndWrapsTheRows() {
		SessionHistoryEntry entry = new SessionHistoryEntry(
				SESSION_ID, 0, "Home row", OffsetDateTime.now(),
				new BigDecimal("97.50"), 380, 720, 128, true);
		when(history.recent(USER_ID, 3)).thenReturn(Flux.just(entry));

		SessionHistoryResponse response = controller.recent(3, PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.sessions()).containsExactly(entry);
		verify(history).recent(USER_ID, 3);
	}

	@Test
	void completeForwardsTheOwnedSession() {
		SessionCompletionResponse expected = new SessionCompletionResponse(
				SESSION_ID, 0, "COMPLETED",
				new SessionMetrics(120, 118, new BigDecimal("98.33"), 400, 800, 0),
				true, 1, 0, null, 450, 900, 96.0, 120, List.of());
		when(completion.complete(USER_ID, SESSION_ID)).thenReturn(Mono.just(expected));

		assertThat(controller.complete(SESSION_ID, PRINCIPAL).block()).isEqualTo(expected);
		verify(completion).complete(USER_ID, SESSION_ID);
	}

	private static TypingSession session() {
		return new TypingSession(
				SESSION_ID,
				USER_ID,
				UUID.randomUUID(),
				0,
				"STATIC",
				40,
				"asdfjkl; ",
				"asdf jkl; asdf jkl;",
				"ACTIVE",
				OffsetDateTime.now(),
				null,
				0,
				0,
				null,
				null,
				null,
				0,
				null,
				null);
	}
}
