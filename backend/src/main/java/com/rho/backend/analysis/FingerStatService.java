package com.rho.backend.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Owns per-finger aggregates: records them at completion, reports the weakest
 * reaches, and exposes the one reach the next drill should lean on.
 */
@Service
public class FingerStatService {

	private static final Logger log = LoggerFactory.getLogger(FingerStatService.class);

	/** One stumble is noise; a repeated reach is a pattern. */
	static final int MIN_FOCUS_ATTEMPTS = 2;
	static final double MIN_FOCUS_SCORE = 0.1;

	public static final int DEFAULT_TREND_LIMIT = 5;
	public static final int MAX_TREND_LIMIT = 10;

	private final FingerStatRepository repository;

	public FingerStatService(FingerStatRepository repository) {
		this.repository = repository;
	}

	/**
	 * Merges this session's finger counters and keeps one copy for the trend,
	 * inside the caller's completion transaction.
	 */
	public Mono<Void> recordSession(
			UUID sessionId, UUID userId, int tier, List<KeystrokeEvent> events) {
		List<FingerStat> stats = FingerAnalyzer.analyze(events);
		return repository.merge(userId, tier, stats)
				.then(repository.recordSession(sessionId, userId, tier, stats));
	}

	/** Recent sessions for one finger, oldest first; empty when nothing matches. */
	public Mono<FingerTrend> trend(UUID userId, int tier, String finger, int limit) {
		Finger parsed;
		try {
			parsed = Finger.valueOf(finger == null ? "" : finger);
		}
		catch (IllegalArgumentException error) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Unknown finger " + finger));
		}
		if (limit < 1 || limit > MAX_TREND_LIMIT) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_TREND_LIMIT));
		}

		return repository.findRecentAttempts(userId, tier, parsed, limit)
				.collectList()
				.map(newestFirst -> {
					List<FingerTrend.Attempt> oldestFirst = new ArrayList<>(newestFirst);
					Collections.reverse(oldestFirst);
					return new FingerTrend(
							tier, parsed.name(), parsed.label(), List.copyOf(oldestFirst));
				});
	}

	public Mono<FingerReport> report(UUID userId, int tier) {
		return repository.findByUserAndTier(userId, tier)
				.collectList()
				.map(stats -> new FingerReport(tier, stats.stream()
						.sorted(Comparator.comparingDouble(FingerStat::score).reversed()
								.thenComparing(FingerStat::finger))
						.map(FingerReport.Entry::from)
						.toList()));
	}

	/**
	 * The reach the next drill should exercise, if one is clearly weak.
	 * Analysis is an accelerator, never a prerequisite, so a failing read
	 * simply means no finger targeting.
	 */
	public Mono<Optional<Finger>> focus(UUID userId, int tier) {
		return repository.findByUserAndTier(userId, tier)
				.filter(stat -> stat.attempts() >= MIN_FOCUS_ATTEMPTS)
				.filter(stat -> stat.score() >= MIN_FOCUS_SCORE)
				.sort(Comparator.comparingDouble(FingerStat::score).reversed()
						.thenComparing(FingerStat::finger))
				.next()
				.map(stat -> Optional.of(stat.finger()))
				.defaultIfEmpty(Optional.empty())
				.onErrorResume(error -> {
					log.warn("Could not load finger stats for tier {}", tier, error);
					return Mono.just(Optional.empty());
				});
	}
}
