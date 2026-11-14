package com.rho.backend.drills;

import java.util.List;

import reactor.core.publisher.Mono;

/**
 * Seam for generated drills. Implementations may return {@link Mono#empty()}
 * when generation is unavailable; callers always have the static pool.
 */
public interface DrillGenerator {

	Mono<String> generate(TierDefinition tier, String exclude);

	/**
	 * Generates a drill that leans on the typist's weak transitions. The
	 * default ignores them, so simple generators keep working unchanged.
	 */
	default Mono<String> generate(TierDefinition tier, List<String> focusPairs, String exclude) {
		return generate(tier, exclude);
	}
}
