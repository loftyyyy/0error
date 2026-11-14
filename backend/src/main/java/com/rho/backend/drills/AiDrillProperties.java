package com.rho.backend.drills;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds for the background AI drill cache. The cache is an accelerator only:
 * when it is empty or disabled, drills come from the static pool.
 */
@ConfigurationProperties(prefix = "app.drills.ai")
public record AiDrillProperties(boolean enabled, int cacheSize, int ttlMinutes, int maxFocusKeys) {

	public AiDrillProperties {
		if (cacheSize <= 0) {
			cacheSize = 2;
		}
		if (ttlMinutes <= 0) {
			ttlMinutes = 24 * 60;
		}
		if (maxFocusKeys <= 0) {
			maxFocusKeys = 32;
		}
	}
}
