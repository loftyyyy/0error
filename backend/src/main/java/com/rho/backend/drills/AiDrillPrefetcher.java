package com.rho.backend.drills;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Warms the generated-drill cache after startup. A failure here is invisible to
 * users because the static pool already answers requests.
 */
@Component
public class AiDrillPrefetcher {

	private final AiDrillCache cache;
	private final TierCatalogue catalogue;

	public AiDrillPrefetcher(AiDrillCache cache, TierCatalogue catalogue) {
		this.cache = cache;
		this.catalogue = catalogue;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmOnStartup() {
		if (!cache.enabled()) {
			return;
		}
		catalogue.all().forEach(tier -> cache.refill(tier, null));
	}
}
