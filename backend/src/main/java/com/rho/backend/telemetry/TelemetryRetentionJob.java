package com.rho.backend.telemetry;

import java.time.Duration;
import java.time.Instant;

import com.rho.backend.config.TelemetryProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Purges raw keystrokes older than the retention window. Aggregates already
 * live on the session row, so tier progress is never lost.
 */
@Component
public class TelemetryRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);

	private final KeystrokeRetention retention;
	private final TelemetryProperties properties;

	public TelemetryRetentionJob(KeystrokeRetention retention, TelemetryProperties properties) {
		this.retention = retention;
		this.properties = properties;
	}

	@Scheduled(cron = "${app.telemetry.purge-cron:0 30 3 * * *}")
	public void purgeExpired() {
		purge(properties.rawRetentionDays(), properties.purgeBatchSize())
				.doOnNext(deleted -> log.info("Purged {} raw keystrokes older than {} days",
						deleted, properties.rawRetentionDays()))
				.doOnError(error -> log.error("Raw keystroke retention purge failed", error))
				.subscribe();
	}

	/**
	 * Repeats bounded deletes until a slice comes back short. Returns the total
	 * number of removed rows.
	 */
	public Mono<Long> purge(int retentionDays, int batchSize) {
		Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));

		return retention.deleteBatch(cutoff, batchSize)
				.expand(deleted -> deleted == batchSize
						? retention.deleteBatch(cutoff, batchSize)
						: Mono.empty())
				.reduce(0L, Long::sum);
	}
}
