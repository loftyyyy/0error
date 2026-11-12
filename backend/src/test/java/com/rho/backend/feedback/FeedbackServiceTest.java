package com.rho.backend.feedback;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.sessions.TypingSession;
import com.rho.backend.sessions.TypingSessionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();

	private final TypingSessionRepository sessions = mock(TypingSessionRepository.class);
	private final FeedbackGenerator generator = mock(FeedbackGenerator.class);

	@Test
	void streamsGeneratedTokensForACompletedSession() {
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session("COMPLETED")));
		when(generator.stream(any())).thenReturn(Flux.just("Nice rhythm.", " Keep it."));

		StepVerifier.create(service().stream(USER_ID, SESSION_ID))
				.expectNext("Nice rhythm.", " Keep it.")
				.verifyComplete();
	}

	@Test
	void emptyGeneratorFallsBackToDeterministicCoaching() {
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session("COMPLETED")));
		when(generator.stream(any())).thenReturn(Flux.empty());

		StepVerifier.create(service().stream(USER_ID, SESSION_ID))
				.expectNext(String.join(" ", FallbackFeedback.messages(context())))
				.verifyComplete();
	}

	@Test
	void generatorErrorEndsWithTheClosingLine() {
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session("COMPLETED")));
		when(generator.stream(any())).thenReturn(Flux.error(new IllegalStateException("boom")));

		StepVerifier.create(service().stream(USER_ID, SESSION_ID))
				.expectNext(" " + FallbackFeedback.closingLine())
				.verifyComplete();
	}

	@Test
	void activeSessionIsAConflict() {
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session("ACTIVE")));

		StepVerifier.create(service().stream(USER_ID, SESSION_ID))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.CONFLICT);
				})
				.verify();
	}

	@Test
	void unknownSessionIsNotFound() {
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.empty());

		StepVerifier.create(service().stream(USER_ID, SESSION_ID))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.NOT_FOUND);
				})
				.verify();
	}

	private FeedbackService service() {
		return new FeedbackService(sessions, new TierCatalogue(), generator);
	}

	private static FeedbackContext context() {
		return new FeedbackContext(
				0, "Home row", 140, 138, new BigDecimal("98.57"), 280, 620, 0);
	}

	private static TypingSession session(String status) {
		OffsetDateTime now = OffsetDateTime.now();
		return new TypingSession(
				SESSION_ID,
				USER_ID,
				UUID.randomUUID(),
				0,
				"STATIC",
				40,
				"asdfjkl; ",
				status,
				now,
				now,
				140,
				138,
				new BigDecimal("98.57"),
				280,
				620,
				0,
				now,
				now);
	}
}
