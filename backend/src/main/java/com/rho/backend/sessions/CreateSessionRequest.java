package com.rho.backend.sessions;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Only the idempotency key and the drill ids are accepted. Tier, charset,
 * length, source, and text all come from the server-issued drills.
 */
public record CreateSessionRequest(
		@NotNull UUID clientSessionId,
		@NotEmpty @Size(max = 5) List<@NotNull UUID> drillIds) {
}
