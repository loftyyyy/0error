package com.rho.backend.telemetry;

import java.util.UUID;

/**
 * Outcome of one batch. {@code duplicates} counts events already stored, so a
 * client can tell a partial retry from a fully duplicate one.
 */
public record TelemetryIngestResponse(
		UUID sessionId,
		int received,
		int inserted,
		int duplicates) {
}
