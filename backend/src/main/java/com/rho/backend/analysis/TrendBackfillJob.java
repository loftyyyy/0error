package com.rho.backend.analysis;

import com.rho.backend.config.TelemetryProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rebuilds trend rows for older sessions once a day. It is a repair, not part
 * of the request path: a failure is logged and the next run retries.
 */
@Component
public class TrendBackfillJob {

	private static final Logger log = LoggerFactory.getLogger(TrendBackfillJob.class);

	private final TrendBackfill backfill;
	private final TelemetryProperties properties;
	private final int limit;

	public TrendBackfillJob(
			TrendBackfill backfill,
			TelemetryProperties properties,
			@Value("${app.analysis.backfill-limit:1000}") int limit) {
		this.backfill = backfill;
		this.properties = properties;
		this.limit = limit;
	}

	@Scheduled(cron = "${app.analysis.backfill-cron:0 45 3 * * *}")
	public void rebuildMissingTrends() {
		backfill.backfill(properties.rawRetentionDays(), limit)
				.doOnNext(count -> log.info("Rebuilt trend rows for {} completed sessions", count))
				.doOnError(error -> log.error("Trend backfill failed", error))
				.subscribe();
	}
}
