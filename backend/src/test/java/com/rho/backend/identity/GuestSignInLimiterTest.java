package com.rho.backend.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestSignInLimiterTest {

	private static final String ADDRESS = "203.0.113.7";

	private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

	private final GuestSignInLimiter limiter = new GuestSignInLimiter(clock);

	@Test
	void admitsOneBurstThenRefuses() {
		for (int guest = 0; guest < GuestSignInLimiter.CAPACITY; guest++) {
			assertThat(limiter.tryAcquire(ADDRESS)).as("guest %d of the burst", guest).isTrue();
		}

		assertThat(limiter.tryAcquire(ADDRESS)).isFalse();
	}

	@Test
	void refillsOneGuestPerInterval() {
		drain(ADDRESS);

		clock.advance(GuestSignInLimiter.REFILL);

		assertThat(limiter.tryAcquire(ADDRESS)).isTrue();
		assertThat(limiter.tryAcquire(ADDRESS)).isFalse();
	}

	@Test
	void aRefusedAttemptDoesNotPushTheRefillAway() {
		drain(ADDRESS);

		clock.advance(GuestSignInLimiter.REFILL.minusMillis(1));
		assertThat(limiter.tryAcquire(ADDRESS)).isFalse();

		clock.advance(Duration.ofMillis(1));
		assertThat(limiter.tryAcquire(ADDRESS)).isTrue();
	}

	@Test
	void eachAddressKeepsItsOwnBudget() {
		drain(ADDRESS);

		assertThat(limiter.tryAcquire("198.51.100.4")).isTrue();
		assertThat(limiter.tryAcquire(ADDRESS)).isFalse();
	}

	@Test
	void aMissingAddressFallsBackToOneSharedBudget() {
		drain(null);

		assertThat(limiter.tryAcquire("")).isFalse();
		assertThat(limiter.tryAcquire(ADDRESS)).isTrue();
	}

	@Test
	void retryAfterMatchesTheRefill() {
		assertThat(limiter.retryAfterSeconds()).isEqualTo(GuestSignInLimiter.REFILL.toSeconds());
	}

	private void drain(String address) {
		for (int guest = 0; guest < GuestSignInLimiter.CAPACITY; guest++) {
			assertThat(limiter.tryAcquire(address)).isTrue();
		}
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
