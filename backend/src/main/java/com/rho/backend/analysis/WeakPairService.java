package com.rho.backend.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Owns pair aggregates: records them at completion, reports them to the client,
 * and turns them into a small focus list for drill selection.
 */
@Service
public class WeakPairService {

	private static final Logger log = LoggerFactory.getLogger(WeakPairService.class);

	/** More than three focus pairs stops feeling like a targeted lesson. */
	static final int FOCUS_LIMIT = 3;
	/** One stumble is noise; a repeated transition is a pattern. */
	static final int MIN_FOCUS_ATTEMPTS = 2;
	static final double MIN_FOCUS_SCORE = 0.1;

	public static final int DEFAULT_TREND_LIMIT = 5;
	public static final int MAX_TREND_LIMIT = 10;

	private final WeakPairRepository repository;

	public WeakPairService(WeakPairRepository repository) {
		this.repository = repository;
	}

	/**
	 * Merges this session's pairs and keeps one copy for the trend, inside the
	 * caller's completion transaction.
	 */
	public Mono<Void> recordSession(
			UUID sessionId, UUID userId, int tier, List<KeystrokeEvent> events) {
		List<WeakPair> pairs = WeakPairAnalyzer.analyze(events);
		return repository.merge(userId, tier, pairs)
				.then(repository.recordSession(sessionId, userId, tier, pairs));
	}

	/** Recent sessions for one pair, oldest first; empty when nothing matches. */
	public Mono<WeakPairTrend> trend(UUID userId, int tier, String pair, int limit) {
		if (pair == null || pair.length() != 2
				|| !Attempts.isPrintableCharacter(pair.substring(0, 1))
				|| !Attempts.isPrintableCharacter(pair.substring(1, 2))) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "pair must be two printable characters"));
		}
		if (limit < 1 || limit > MAX_TREND_LIMIT) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_TREND_LIMIT));
		}

		return repository.findRecentAttempts(
						userId, tier, pair.substring(0, 1), pair.substring(1, 2), limit)
				.collectList()
				.map(newestFirst -> {
					List<WeakPairTrend.Attempt> oldestFirst = new ArrayList<>(newestFirst);
					Collections.reverse(oldestFirst);
					return new WeakPairTrend(tier, pair, List.copyOf(oldestFirst));
				});
	}

	public Mono<WeakPairReport> report(UUID userId, int tier) {
		return repository.findByUserAndTier(userId, tier)
				.collectList()
				.map(pairs -> new WeakPairReport(tier, pairs.stream()
						.sorted(Comparator.comparingDouble(WeakPair::score).reversed()
								.thenComparing(WeakPair::pair))
						.map(WeakPairReport.Pair::from)
						.toList()));
	}

	/**
	 * The transitions the next drill should exercise. Analysis is an
	 * accelerator, never a prerequisite, so a failing read simply means no
	 * targeting rather than a broken drill.
	 */
	public Mono<List<String>> focus(UUID userId, int tier) {
		return repository.findByUserAndTier(userId, tier)
				.filter(pair -> pair.attempts() >= MIN_FOCUS_ATTEMPTS)
				.filter(pair -> pair.score() >= MIN_FOCUS_SCORE)
				.sort(Comparator.comparingDouble(WeakPair::score).reversed()
						.thenComparing(WeakPair::pair))
				.take(FOCUS_LIMIT)
				.map(WeakPair::pair)
				.collectList()
				.onErrorResume(error -> {
					log.warn("Could not load weak pairs for tier {}", tier, error);
					return Mono.just(List.of());
				});
	}
}
