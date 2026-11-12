package com.rho.backend.feedback;

import reactor.core.publisher.Flux;

/** Streams short coaching text. Empty means "use the deterministic fallback". */
public interface FeedbackGenerator {

	Flux<String> stream(FeedbackContext context);
}
