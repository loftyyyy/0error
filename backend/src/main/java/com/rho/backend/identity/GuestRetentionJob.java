package com.rho.backend.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the dormant-guest sweep once a day, after sessions have been closed and
 * raw telemetry purged so the remaining rows are stable.
 */
@Component
public class GuestRetentionJob {

	private static final Logger log = LoggerFactory.getLogger(GuestRetentionJob.class);

	private final GuestRetention retention;

	public GuestRetentionJob(GuestRetention retention) {
		this.retention = retention;
	}

	@Scheduled(cron = "${app.identity.guest-sweep-cron:0 45 3 * * *}")
	public void deleteDormant() {
		retention.deleteDormant(GuestRetention.DORMANT_AFTER)
				.doOnNext(count -> log.info("Removed {} dormant guests", count))
				.doOnError(error -> log.error("Dormant guest sweep failed", error))
				.subscribe();
	}
}
