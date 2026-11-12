package com.rho.backend.telemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.rho.backend.config.TelemetryProperties;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryRetentionJobTest {

	@Test
	void deletesBoundedSlicesUntilOneComesBackShort() {
		RecordingRetention retention = new RecordingRetention(2, 2, 1);
		TelemetryRetentionJob job = new TelemetryRetentionJob(
				retention, new TelemetryProperties(30, 500, 2));

		long deleted = job.purge(30, 2).block();

		assertThat(deleted).isEqualTo(5);
		assertThat(retention.calls).isEqualTo(3);
		assertThat(retention.cutoffs).allSatisfy(cutoff ->
				assertThat(Duration.between(cutoff, Instant.now()))
						.isBetween(Duration.ofDays(30).minusSeconds(5), Duration.ofDays(30).plusSeconds(5)));
	}

	@Test
	void stopsAfterAnEmptyFirstSlice() {
		RecordingRetention retention = new RecordingRetention(0);
		TelemetryRetentionJob job = new TelemetryRetentionJob(
				retention, new TelemetryProperties(30, 500, 1_000));

		long deleted = job.purge(30, 1_000).block();

		assertThat(deleted).isZero();
		assertThat(retention.calls).isEqualTo(1);
	}

	private static final class RecordingRetention implements KeystrokeRetention {

		private final List<Integer> batches;
		private final List<Instant> cutoffs = new ArrayList<>();
		private int calls;

		private RecordingRetention(int... batches) {
			this.batches = IntStream.of(batches).boxed().toList();
		}

		@Override
		public Mono<Long> deleteBatch(Instant cutoff, int limit) {
			cutoffs.add(cutoff);
			int index = Math.min(calls, batches.size() - 1);
			calls += 1;
			return Mono.just(batches.get(index).longValue());
		}
	}
}
