package com.rho.backend.drills;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes drill issuances a day after they expire. A consumed id is useless
 * immediately; keeping a day of them leaves room for a late diagnosis.
 */
@Component
public class DrillIssuanceRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(DrillIssuanceRetentionJob.class);
	private static final Duration GRACE = Duration.ofDays(1);

	private final DrillIssuanceStore issuances;

	public DrillIssuanceRetentionJob(DrillIssuanceStore issuances) {
		this.issuances = issuances;
	}

	@Scheduled(cron = "${app.drills.issuance-purge-cron:0 0 4 * * *}")
	public void purgeExpired() {
		issuances.deleteExpiredBefore(OffsetDateTime.now().minus(GRACE))
				.doOnNext(deleted -> log.info("Purged {} expired drill issuances", deleted))
				.doOnError(error -> log.error("Drill issuance purge failed", error))
				.subscribe();
	}
}
