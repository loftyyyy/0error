package com.rho.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Telemetry storage limits. Raw keystrokes age out; session aggregates stay.
 */
@ConfigurationProperties(prefix = "app.telemetry")
public record TelemetryProperties(
		int rawRetentionDays,
		int maxBatchSize,
		int purgeBatchSize) {

	public TelemetryProperties {
		if (rawRetentionDays <= 0) {
			rawRetentionDays = 30;
		}
		if (maxBatchSize <= 0) {
			maxBatchSize = 500;
		}
		if (purgeBatchSize <= 0) {
			purgeBatchSize = 1_000;
		}
	}
}
