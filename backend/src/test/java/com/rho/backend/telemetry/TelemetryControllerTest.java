package com.rho.backend.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelemetryControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final TelemetryIngestService ingest = mock(TelemetryIngestService.class);
	private final TelemetryController controller = new TelemetryController(ingest);

	@Test
	void partialOrFullInsertReturns200() {
		when(ingest.ingest(eq(USER_ID), eq(SESSION_ID), any()))
				.thenReturn(Mono.just(new TelemetryIngestResponse(SESSION_ID, 3, 2, 1)));

		ResponseEntity<TelemetryIngestResponse> response =
				controller.ingest(SESSION_ID, request(), PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().inserted()).isEqualTo(2);
		assertThat(response.getBody().duplicates()).isEqualTo(1);
	}

	@Test
	void fullyDuplicateBatchReturns409WithCounts() {
		when(ingest.ingest(eq(USER_ID), eq(SESSION_ID), any()))
				.thenReturn(Mono.just(new TelemetryIngestResponse(SESSION_ID, 2, 0, 2)));

		ResponseEntity<TelemetryIngestResponse> response =
				controller.ingest(SESSION_ID, request(), PRINCIPAL).block();

		assertThat(response).isNotNull();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().inserted()).isZero();
		assertThat(response.getBody().duplicates()).isEqualTo(2);
	}

	@Test
	void missingPrincipalIsUnauthorized() {
		StepVerifier.create(controller.ingest(SESSION_ID, request(), null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.UNAUTHORIZED);
				})
				.verify();
	}

	@Test
	void notFoundFromOwnershipCheckPropagates() {
		when(ingest.ingest(eq(USER_ID), eq(SESSION_ID), any()))
				.thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")));

		StepVerifier.create(controller.ingest(SESSION_ID, request(), PRINCIPAL))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.NOT_FOUND);
				})
				.verify();
	}

	private static TelemetryBatchRequest request() {
		return new TelemetryBatchRequest(
				"batch-1",
				SESSION_ID,
				Instant.now(),
				List.of(new RawKeystroke(0, 0, RawKeystroke.KEY, "a", "a", true, 100, 100, false)));
	}
}
