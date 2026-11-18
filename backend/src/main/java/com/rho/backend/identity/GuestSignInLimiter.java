package com.rho.backend.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Caps how many brand-new guests one address can create. A guest costs a row and
 * a token hash, which is nothing on a laptop but a public host would let a loop
 * fill the table. The bucket lives in this process: counts reset on restart and
 * instances do not share them, which is a guard rather than a fortress and keeps
 * Postgres the only dependency.
 *
 * <p>
 * The capacity is deliberately larger than a class behind one school address, so
 * a room full of first-time typists signs in at once; the refill only paces the
 * ones that arrive after that burst.
 */
@Component
public class GuestSignInLimiter {

	static final int CAPACITY = 30;

	static final Duration REFILL = Duration.ofSeconds(2);

	/** Above this many tracked addresses, idle buckets are dropped. */
	private static final int MAX_TRACKED = 10_000;

	private static final Duration IDLE = Duration.ofMinutes(10);

	private final Clock clock;

	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

	public GuestSignInLimiter() {
		this(Clock.systemUTC());
	}

	GuestSignInLimiter(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Takes one token for a new guest. A refused attempt costs nothing, so a
	 * client that retries too early does not push its own refill further away.
	 */
	public boolean tryAcquire(String clientAddress) {
		long now = clock.millis();
		if (buckets.size() >= MAX_TRACKED) {
			buckets.entrySet().removeIf(entry -> entry.getValue().idle(now));
		}
		return buckets.computeIfAbsent(key(clientAddress), address -> new Bucket(now)).take(now);
	}

	/** Seconds until one more new guest fits, for the Retry-After header. */
	public long retryAfterSeconds() {
		return REFILL.toSeconds();
	}

	private static String key(String clientAddress) {
		return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
	}

	private static final class Bucket {

		private int tokens;

		private long updatedAt;

		Bucket(long now) {
			this.tokens = CAPACITY;
			this.updatedAt = now;
		}

		synchronized boolean take(long now) {
			long elapsed = Math.max(0, now - updatedAt);
			long gained = elapsed / REFILL.toMillis();
			if (gained > 0) {
				tokens = (int) Math.min(CAPACITY, tokens + gained);
				updatedAt += gained * REFILL.toMillis();
			}
			if (tokens == 0) {
				return false;
			}
			tokens--;
			return true;
		}

		synchronized boolean idle(long now) {
			return now - updatedAt >= IDLE.toMillis();
		}
	}
}
