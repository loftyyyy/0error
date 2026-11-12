package com.rho.backend.feedback;

import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final FeedbackService feedback = mock(FeedbackService.class);

	@Test
	void streamsTokensThenADoneEvent() {
		when(feedback.stream(USER_ID, SESSION_ID)).thenReturn(Flux.just("Steady.", " Breathe."));

		StepVerifier.create(controller().feedback(SESSION_ID, PRINCIPAL))
				.assertNext(event -> {
					assertThat(event.event()).isEqualTo("token");
					assertThat(event.data()).isEqualTo("Steady.");
				})
				.assertNext(event -> {
					assertThat(event.event()).isEqualTo("token");
					assertThat(event.data()).isEqualTo(" Breathe.");
				})
				.assertNext(event -> {
					assertThat(event.event()).isEqualTo("done");
					assertThat(event.data()).isEqualTo("[DONE]");
				})
				.verifyComplete();
	}

	@Test
	void missingPrincipalIsUnauthorized() {
		StepVerifier.create(controller().feedback(SESSION_ID, null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.UNAUTHORIZED);
				})
				.verify();
	}

	@Test
	void serviceErrorsPropagate() {
		when(feedback.stream(USER_ID, SESSION_ID))
				.thenReturn(Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")));

		StepVerifier.create(controller().feedback(SESSION_ID, PRINCIPAL))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.NOT_FOUND);
				})
				.verify();
	}

	private FeedbackController controller() {
		return new FeedbackController(feedback);
	}
}
