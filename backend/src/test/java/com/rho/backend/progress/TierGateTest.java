package com.rho.backend.progress;

import java.math.BigDecimal;

import com.rho.backend.telemetry.SessionMetrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TierGateTest {

	@Test
	void passesTheBaseGateAtTheExactThresholds() {
		TierGateResult result = TierGate.evaluate(metrics(120, "96.00", 450, 900), 0);

		assertThat(result.passed()).isTrue();
		assertThat(result.failures()).isEmpty();
		assertThat(result.medianThresholdMs()).isEqualTo(450);
		assertThat(result.p95ThresholdMs()).isEqualTo(900);
	}

	@Test
	void failsEveryMissedCriterion() {
		TierGateResult result = TierGate.evaluate(metrics(50, "95.99", 451, 901), 0);

		assertThat(result.passed()).isFalse();
		assertThat(result.failures()).hasSize(4);
		assertThat(result.failures()).anyMatch(failure -> failure.startsWith("accuracy"));
		assertThat(result.failures()).anyMatch(failure -> failure.startsWith("median"));
		assertThat(result.failures()).anyMatch(failure -> failure.startsWith("p95"));
		assertThat(result.failures()).anyMatch(failure -> failure.startsWith("keystrokes"));
	}

	@Test
	void missingCadenceFailsTheGate() {
		TierGateResult result = TierGate.evaluate(
				new SessionMetrics(130, 130, new BigDecimal("100.00"), null, null, 0), 0);

		assertThat(result.passed()).isFalse();
		assertThat(result.failures()).anyMatch(failure -> failure.startsWith("median"));
		assertThat(result.failures()).anyMatch(failure -> failure.startsWith("p95"));
	}

	@Test
	void everyThirdFailureRelaxesCadenceByFifteenPercent() {
		assertThat(TierGate.relax(TierGate.BASE_MEDIAN_MS, 0)).isEqualTo(450);
		assertThat(TierGate.relax(TierGate.BASE_MEDIAN_MS, 2)).isEqualTo(450);
		assertThat(TierGate.relax(TierGate.BASE_MEDIAN_MS, 3)).isEqualTo(383);
		assertThat(TierGate.relax(TierGate.BASE_MEDIAN_MS, 6)).isEqualTo(325);
		assertThat(TierGate.relax(TierGate.BASE_P95_MS, 3)).isEqualTo(765);

		TierGateResult relaxed = TierGate.evaluate(metrics(130, "98.00", 350, 700), 3);

		assertThat(relaxed.passed()).isTrue();
		assertThat(relaxed.medianThresholdMs()).isEqualTo(383);
		assertThat(relaxed.p95ThresholdMs()).isEqualTo(765);
	}

	private static SessionMetrics metrics(
			int keystrokes, String accuracy, Integer median, Integer p95) {
		return new SessionMetrics(
				keystrokes,
				keystrokes,
				new BigDecimal(accuracy),
				median,
				p95,
				0);
	}
}
