package com.rho.backend.drills;

import java.util.List;

import reactor.core.publisher.Mono;

/**
 * Seam for generated drills. Implementations may return {@link Mono#empty()}
 * when generation is unavailable; callers always have the static pool.
 */
public interface DrillGenerator {

	Mono<String> generate(TierDefinition tier, String exclude);

	/** Generates a drill that leans on the typist's weak transitions. */
	default Mono<String> generate(TierDefinition tier, List<String> focusPairs, String exclude) {
		return generate(tier, exclude);
	}

	/**
	 * Generates a drill that leans on weak transitions or a weak reach. The
	 * default passes the pairs along, so simple generators keep working.
	 */
	default Mono<String> generate(TierDefinition tier, DrillFocus focus, String exclude) {
		return generate(tier, focus.pairs(), exclude);
	}
}
