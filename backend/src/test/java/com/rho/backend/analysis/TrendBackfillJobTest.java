package com.rho.backend.analysis;

import com.rho.backend.config.TelemetryProperties;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendBackfillJobTest {

	private final TrendBackfill backfill = mock(TrendBackfill.class);
	private final TelemetryProperties properties = new TelemetryProperties(30, 500, 1_000);
	private final TrendBackfillJob job = new TrendBackfillJob(backfill, properties, 250);

	@Test
	void runsWithTheRetentionWindowAndConfiguredBatch() {
		when(backfill.backfill(30, 250)).thenReturn(Mono.just(2L));

		job.rebuildMissingTrends();

		verify(backfill).backfill(30, 250);
	}

	@Test
	void aFailedRunIsLoggedNotThrown() {
		when(backfill.backfill(30, 250))
				.thenReturn(Mono.error(new IllegalStateException("database down")));

		assertThatCode(job::rebuildMissingTrends).doesNotThrowAnyException();
	}
}
