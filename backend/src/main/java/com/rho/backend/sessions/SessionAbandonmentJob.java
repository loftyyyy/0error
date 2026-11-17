package com.rho.backend.sessions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the abandonment sweep once a day, before the retention purge.
 */
@Component
public class SessionAbandonmentJob {

	private static final Logger log = LoggerFactory.getLogger(SessionAbandonmentJob.class);

	private final SessionAbandonment abandonment;

	public SessionAbandonmentJob(SessionAbandonment abandonment) {
		this.abandonment = abandonment;
	}

	@Scheduled(cron = "${app.sessions.abandon-cron:0 15 3 * * *}")
	public void abandonStale() {
		abandonment.abandonStale(SessionAbandonment.STALE_AFTER)
				.doOnNext(count -> log.info("Marked {} stale sessions as ABANDONED", count))
				.doOnError(error -> log.error("Session abandonment sweep failed", error))
				.subscribe();
	}
}
