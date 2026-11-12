package com.rho.backend.telemetry;

import java.time.Instant;

import reactor.core.publisher.Mono;

/** Storage seam for the retention purge, kept small so batching is testable. */
public interface KeystrokeRetention {

	/** Deletes one bounded slice and returns the number of removed rows. */
	Mono<Long> deleteBatch(Instant cutoff, int limit);
}
