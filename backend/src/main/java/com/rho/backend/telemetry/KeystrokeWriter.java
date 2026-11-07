package com.rho.backend.telemetry;

import java.util.List;
import java.util.UUID;

import reactor.core.publisher.Mono;

/** Storage seam for raw keystrokes. Returns the number of rows actually inserted. */
public interface KeystrokeWriter {

	Mono<Integer> insertIgnoringDuplicates(UUID sessionId, List<RawKeystroke> events);
}
