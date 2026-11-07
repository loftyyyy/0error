package com.rho.backend.telemetry;

import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/telemetry")
public class TelemetryController {

	private final TelemetryIngestService ingest;

	public TelemetryController(TelemetryIngestService ingest) {
		this.ingest = ingest;
	}

	/**
	 * 200 when at least one keystroke was new, 409 when the whole batch was
	 * already stored. Both are safe to treat as delivered by the outbox.
	 */
	@PostMapping
	public Mono<ResponseEntity<TelemetryIngestResponse>> ingest(
			@PathVariable UUID sessionId,
			@Valid @RequestBody TelemetryBatchRequest request,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}

		return ingest.ingest(principal.userId(), sessionId, request)
				.map(result -> ResponseEntity
						.status(result.inserted() == 0 ? HttpStatus.CONFLICT : HttpStatus.OK)
						.body(result));
	}
}
