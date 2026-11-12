package com.rho.backend.drills;

import reactor.core.publisher.Mono;

/**
 * Seam for generated drills. Implementations may return {@link Mono#empty()}
 * when generation is unavailable; callers always have the static pool.
 */
public interface DrillGenerator {

	Mono<String> generate(TierDefinition tier, String exclude);
}
