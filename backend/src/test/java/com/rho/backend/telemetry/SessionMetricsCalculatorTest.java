package com.rho.backend.telemetry;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMetricsCalculatorTest {

	@Test
	void emptySessionHasNoAggregates() {
		SessionMetrics metrics = SessionMetricsCalculator.calculate(List.of());

		assertThat(metrics.keystrokeCount()).isZero();
		assertThat(metrics.accuracy()).isNull();
		assertThat(metrics.medianInterKeyMs()).isNull();
		assertThat(metrics.p95InterKeyMs()).isNull();
		assertThat(metrics.hesitationCount()).isZero();
	}

	@Test
	void warmupKeysAreStoredButNeverScored() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 0, true),
				key(1, 1, "s", true, 100, true),
				key(2, 2, "d", true, 200, true),
				key(3, 3, "f", true, 300, false));

		SessionMetrics metrics = SessionMetricsCalculator.calculate(events);

		assertThat(metrics.keystrokeCount()).isEqualTo(1);
		assertThat(metrics.correctCount()).isEqualTo(1);
		assertThat(metrics.accuracy()).isEqualByComparingTo("100.00");
	}

	@Test
	void accuracyIsFirstAttemptPerPosition() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "x", false, 100, false),
				backspace(1, 0, 200),
				key(2, 0, "a", true, 300, false),
				key(3, 1, "s", true, 400, false));

		SessionMetrics metrics = SessionMetricsCalculator.calculate(events);

		// Two distinct positions, one of them wrong first: corrections never erase an error.
		assertThat(metrics.keystrokeCount()).isEqualTo(3);
		assertThat(metrics.correctCount()).isEqualTo(1);
		assertThat(metrics.accuracy()).isEqualByComparingTo("50.00");
	}

	@Test
	void cadenceUsesMedianAndP95OfInterKeyGaps() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100, false),
				key(1, 1, "s", true, 300, false),
				key(2, 2, "d", true, 700, false),
				key(3, 3, "f", true, 1_200, false),
				key(4, 4, "g", true, 2_000, false));

		SessionMetrics metrics = SessionMetricsCalculator.calculate(events);

		// Gaps: 200, 400, 500, 800.
		assertThat(metrics.medianInterKeyMs()).isEqualTo(450);
		assertThat(metrics.p95InterKeyMs()).isEqualTo(800);
		assertThat(metrics.hesitationCount()).isZero();
	}

	@Test
	void gapOverThresholdIsExcludedFromCadenceAndCountedAsHesitation() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 0, false),
				key(1, 1, "s", true, 100, false),
				key(2, 2, "d", true, 100 + SessionMetricsCalculator.HESITATION_GAP_MS + 1, false),
				key(3, 3, "f", true, 200 + SessionMetricsCalculator.HESITATION_GAP_MS + 1, false));

		SessionMetrics metrics = SessionMetricsCalculator.calculate(events);

		assertThat(metrics.hesitationCount()).isEqualTo(1);
		assertThat(metrics.medianInterKeyMs()).isEqualTo(100);
		assertThat(metrics.p95InterKeyMs()).isEqualTo(100);
	}

	@Test
	void backspaceDoesNotEnterCadenceButTheCorrectedKeyDoes() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100, false),
				backspace(1, 0, 250),
				key(2, 0, "a", true, 400, false));

		SessionMetrics metrics = SessionMetricsCalculator.calculate(events);

		assertThat(metrics.keystrokeCount()).isEqualTo(2);
		assertThat(metrics.medianInterKeyMs()).isEqualTo(300);
	}

	@Test
	void singleKeyHasAccuracyButNoCadence() {
		SessionMetrics metrics = SessionMetricsCalculator.calculate(
				List.of(key(0, 0, "a", true, 100, false)));

		assertThat(metrics.accuracy()).isEqualByComparingTo(BigDecimal.valueOf(100).setScale(2));
		assertThat(metrics.medianInterKeyMs()).isNull();
		assertThat(metrics.p95InterKeyMs()).isNull();
	}

	private static KeystrokeEvent key(
			int seq, int position, String actual, boolean correct, int tMs, boolean warmup) {
		return new KeystrokeEvent(
				(long) seq, null, seq, position, RawKeystroke.KEY,
				"x", actual, correct, tMs, 100, warmup, null);
	}

	private static KeystrokeEvent backspace(int seq, int position, int tMs) {
		return new KeystrokeEvent(
				(long) seq, null, seq, position, RawKeystroke.BACKSPACE,
				"a", null, true, tMs, null, false, null);
	}
}
