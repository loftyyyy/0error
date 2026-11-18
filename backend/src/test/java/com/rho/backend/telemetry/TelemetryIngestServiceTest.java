package com.rho.backend.telemetry;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.rho.backend.sessions.TypingSession;
import com.rho.backend.sessions.TypingSessionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
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
	void clientCorrectnessFlagIsIgnored() {
		givenOwnedSession("ab");
		when(writer.insertIgnoringDuplicates(any(), anyList())).thenReturn(Mono.just(4));
		when(metrics.recompute(SESSION_ID)).thenReturn(Mono.just(new SessionMetrics(
				4, 1, new java.math.BigDecimal("25.00"), 100, 100, 0)));

		List<RawKeystroke> sent = List.of(
				new RawKeystroke(0, 0, RawKeystroke.KEY, "a", "s", true, 0, 100, false),
				new RawKeystroke(1, 1, RawKeystroke.KEY, "b", "b", false, 100, 100, false),
				new RawKeystroke(2, 1, RawKeystroke.BACKSPACE, "b", null, false, 200, null, false),
				new RawKeystroke(3, 2, RawKeystroke.KEY, null, "x", true, 300, 100, false));

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID,
						new TelemetryBatchRequest("b", SESSION_ID, Instant.now(), sent)))
				.assertNext(response -> assertThat(response.inserted()).isEqualTo(4))
				.verifyComplete();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RawKeystroke>> stored = ArgumentCaptor.forClass(List.class);
		verify(writer).insertIgnoringDuplicates(eq(SESSION_ID), stored.capture());
		assertThat(stored.getValue())
				.extracting(RawKeystroke::correct)
				.containsExactly(false, true, true, false);
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

	@Test
	void expectedCharactersAreRewrittenFromTheIssuedDrillText() {
		givenOwnedSession("asdf");
		when(writer.insertIgnoringDuplicates(any(), anyList())).thenReturn(Mono.just(5));
		when(metrics.recompute(SESSION_ID)).thenReturn(Mono.just(new SessionMetrics(
				5, 4, new java.math.BigDecimal("80.00"), 100, 100, 0)));

		// The client claims these expected characters and flags every key correct.
		List<RawKeystroke> sent = List.of(
				new RawKeystroke(0, 0, RawKeystroke.KEY, "z", "a", true, 0, 100, false),
				new RawKeystroke(1, 1, RawKeystroke.KEY, "z", "s", true, 100, 100, false),
				new RawKeystroke(2, 2, RawKeystroke.KEY, "z", "d", true, 200, 100, false),
				new RawKeystroke(3, 3, RawKeystroke.KEY, "z", "f", true, 300, 100, false),
				new RawKeystroke(4, 4, RawKeystroke.KEY, "a", "a", true, 400, 100, false));

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID,
						new TelemetryBatchRequest("b", SESSION_ID, Instant.now(), sent)))
				.assertNext(response -> assertThat(response.inserted()).isEqualTo(5))
				.verifyComplete();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RawKeystroke>> stored = ArgumentCaptor.forClass(List.class);
		verify(writer).insertIgnoringDuplicates(eq(SESSION_ID), stored.capture());
		assertThat(stored.getValue())
				.extracting(RawKeystroke::expected)
				.containsExactly("a", "s", "d", "f", null);
		assertThat(stored.getValue())
				.extracting(RawKeystroke::correct)
				.containsExactly(true, true, true, true, false);
	}

	@Test
	void telemetryForAClosedSessionIsRefused() {
		givenSessionWithStatus("COMPLETED", "asdf");

		StepVerifier.create(service.ingest(USER_ID, SESSION_ID,
						new TelemetryBatchRequest("b", SESSION_ID, Instant.now(),
								List.of(keystroke(0)))))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					ResponseStatusException status = (ResponseStatusException) error;
					assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(status.getReason()).isEqualTo("Session is already COMPLETED");
				})
				.verify();

		verify(writer, never()).insertIgnoringDuplicates(any(), anyList());
		verify(metrics, never()).recompute(any());
	}

	private void givenOwnedSession() {
		givenOwnedSession("a".repeat(64));
	}

	private void givenSessionWithStatus(String status, String drillText) {
		TypingSession session = new TypingSession(
				SESSION_ID, USER_ID, UUID.randomUUID(), 0, "STATIC", drillText.length(), "asdfjkl;",
				drillText, status, OffsetDateTime.now(), null, 0, 0, null, null, null, 0, null, null);
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session));
	}

	private void givenOwnedSession(String drillText) {
		TypingSession session = new TypingSession(
				SESSION_ID, USER_ID, UUID.randomUUID(), 0, "STATIC", drillText.length(), "asdfjkl;",
				drillText, "ACTIVE", OffsetDateTime.now(), null, 0, 0, null, null, null, 0, null, null);
		when(sessions.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Mono.just(session));
	}

	private static RawKeystroke keystroke(int seq) {
		return new RawKeystroke(seq, seq, RawKeystroke.KEY, "a", "a", true, seq * 100, 100, false);
	}
}
