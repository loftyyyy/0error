package com.rho.backend.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeakPairTest {

	@Test
	void anUnseenPairScoresZero() {
		WeakPair pair = new WeakPair("a", "s", 0, 0, 0, 0);

		assertThat(pair.score()).isZero();
		assertThat(pair.meanDisplayMs()).isZero();
	}

	@Test
	void errorsWeighMoreThanPausesWhichWeighMoreThanLatency() {
		WeakPair errors = new WeakPair("a", "s", 4, 2, 0, 1_200);
		WeakPair pauses = new WeakPair("d", "f", 4, 0, 3, 1_200);
		WeakPair latency = new WeakPair("j", "k", 4, 0, 0, 6_000);

		assertThat(errors.score()).isGreaterThan(pauses.score());
		assertThat(pauses.score()).isGreaterThan(latency.score());
	}

	@Test
	void latencyIsBoundedAndRoundsTheMean() {
		WeakPair slow = new WeakPair("j", "k", 3, 0, 0, 4_500);

		assertThat(slow.meanDisplayMs()).isEqualTo(1_500);
		assertThat(slow.score()).isEqualTo(0.15);
	}

	@Test
	void pairTextIsTheTwoExpectedCharacters() {
		assertThat(new WeakPair(" ", ",", 1, 0, 0, 100).pair()).isEqualTo(" ,");
	}
}
