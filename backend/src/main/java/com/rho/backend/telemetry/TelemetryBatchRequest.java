package com.rho.backend.telemetry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A batch from the IndexedDB outbox. The batch id is trace-only; idempotency is
 * enforced by the {@code (session_id, seq)} unique constraint, so a retried
 * batch can never duplicate a keystroke.
 */
public record TelemetryBatchRequest(
		@Size(max = 64) String batchId,
		@NotNull UUID sessionId,
		Instant sentAt,
		@NotEmpty @Size(max = 500) List<@Valid RawKeystroke> events) {
}
