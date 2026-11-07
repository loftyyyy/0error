package com.rho.backend.telemetry;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.rho.backend.sessions.TypingSession;
import com.rho.backend.sessions.TypingSessionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();

	@Mock
	private TypingSessionRepository sessions;

	@Mock
	private KeystrokeWriter writer;

	@Mock
	private SessionMetricsService metrics;

	@InjectMocks
	private TelemetryIngestService service;

	@Test
	void newEventsAreInsertedAndAggregatesAreRecomputed() {
		givenOwnedSession();
		when(writer.insertIgnoringDuplicates(any(), anyList())).thenReturn(Mono.just(2));
		when(metrics.recompute(SESSION_ID)).thenReturn(Mono.just(new SessionMetrics(
				2, 2, new java.math.BigDecimal("100.00"), 120, 150, 0)));

		TelemetryBatchRequest request = new TelemetryBatchRequest(
				"batch-1", SESSION_ID, Instant.now(), List.of(keystroke(0), keystroke(1)));

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID, request))
				.assertNext(response -> {
					assertThat(response.received()).isEqualTo(2);
					assertThat(response.inserted()).isEqualTo(2);
					assertThat(response.duplicates()).isZero();
				})
				.verifyComplete();

		verify(metrics).recompute(SESSION_ID);
	}

	@Test
	void fullyDuplicateBatchStillRecomputesAndReportsZeroInserted() {
		givenOwnedSession();
		when(writer.insertIgnoringDuplicates(any(), anyList())).thenReturn(Mono.just(0));
		when(metrics.recompute(SESSION_ID)).thenReturn(Mono.just(new SessionMetrics(
				2, 2, new java.math.BigDecimal("100.00"), 120, 150, 0)));

		TelemetryBatchRequest request = new TelemetryBatchRequest(
				"batch-1", SESSION_ID, Instant.now(), List.of(keystroke(0), keystroke(1)));

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID, request))
				.assertNext(response -> {
					assertThat(response.inserted()).isZero();
					assertThat(response.duplicates()).isEqualTo(2);
				})
				.verifyComplete();
	}

	@Test
	void unknownOrForeignSessionIsNotFound() {
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.empty());

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID,
						new TelemetryBatchRequest("b", SESSION_ID, Instant.now(), List.of(keystroke(0)))))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.NOT_FOUND);
				})
				.verify();

		verify(writer, never()).insertIgnoringDuplicates(any(), anyList());
	}

	@Test
	void bodySessionIdMustMatchPath() {
		UUID other = UUID.randomUUID();

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID,
						new TelemetryBatchRequest("b", other, Instant.now(), List.of(keystroke(0)))))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.BAD_REQUEST);
				})
				.verify();

		verify(sessions, never()).findByIdAndUserId(any(), any());
	}

	@Test
	void duplicateSeqWithinBatchIsRejected() {
		StepVerifier.create(service.ingest(USER_ID, SESSION_ID,
						new TelemetryBatchRequest("b", SESSION_ID, Instant.now(),
								List.of(keystroke(0), keystroke(0)))))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.BAD_REQUEST);
				})
				.verify();

		verify(sessions, never()).findByIdAndUserId(any(), any());
	}

	private void givenOwnedSession() {
		TypingSession session = new TypingSession(
				SESSION_ID, USER_ID, UUID.randomUUID(), 0, "STATIC", 40, "asdfjkl;",
				"ACTIVE", OffsetDateTime.now(), null, 0, 0, null, null, null, 0, null, null);
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session));
	}

	private static RawKeystroke keystroke(int seq) {
		return new RawKeystroke(seq, seq, RawKeystroke.KEY, "a", "a", true, seq * 100, 100, false);
	}
}
