package com.rho.backend.drills;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Small in-memory buffer of validated generated drills. A request takes one if
 * it is ready; otherwise the static pool answers and a refill happens in the
 * background. This keeps the LLM off the request path entirely.
 */
@Component
public class AiDrillCache {

	private static final Logger log = LoggerFactory.getLogger(AiDrillCache.class);

	private final Map<Integer, ConcurrentLinkedDeque<CachedDrill>> byTier = new ConcurrentHashMap<>();
	private final Set<Integer> generating = ConcurrentHashMap.newKeySet();

	private final DrillGenerator generator;
	private final DrillValidator validator;
	private final AiDrillProperties properties;

	public AiDrillCache(
			DrillGenerator generator, DrillValidator validator, AiDrillProperties properties) {
		this.generator = generator;
		this.validator = validator;
		this.properties = properties;
	}

	public boolean enabled() {
		return properties.enabled();
	}

	/** Takes the oldest usable drill, dropping expired or excluded entries. */
	public Optional<String> take(int tier, String exclude) {
		ConcurrentLinkedDeque<CachedDrill> queue = byTier.get(tier);
		if (queue == null) {
			return Optional.empty();
		}

		Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.ttlMinutes()));
		CachedDrill candidate;
		while ((candidate = queue.pollFirst()) != null) {
			if (candidate.generatedAt().isBefore(cutoff)) {
				continue;
			}
			if (exclude != null && exclude.equals(candidate.text())) {
				continue;
			}
			return Optional.of(candidate.text());
		}
		return Optional.empty();
	}

	/**
	 * Starts at most one background generation per tier. Invalid output is
	 * discarded; a failure only means the static pool keeps serving.
	 */
	public void refill(TierDefinition tier, String exclude) {
		if (!properties.enabled()) {
			return;
		}

		ConcurrentLinkedDeque<CachedDrill> queue = queue(tier.tier());
		if (queue.size() >= properties.cacheSize()) {
			return;
		}
		if (!generating.add(tier.tier())) {
			return;
		}

		generator.generate(tier, exclude)
				.filter(text -> validator.isValid(text, tier))
				.doOnNext(text -> store(tier.tier(), text))
				.doOnError(error -> log.warn("Drill generation failed for tier {}", tier.tier(), error))
				.onErrorResume(error -> Mono.empty())
				.doFinally(signal -> generating.remove(tier.tier()))
				.subscribe();
	}

	int size(int tier) {
		ConcurrentLinkedDeque<CachedDrill> queue = byTier.get(tier);
		return queue == null ? 0 : queue.size();
	}

	private void store(int tier, String text) {
		ConcurrentLinkedDeque<CachedDrill> queue = queue(tier);
		queue.addLast(new CachedDrill(text, Instant.now()));

		// A slow burst of prefetches must not grow the cache without bound.
		while (queue.size() > properties.cacheSize() + 2) {
			queue.pollFirst();
		}
	}

	private ConcurrentLinkedDeque<CachedDrill> queue(int tier) {
		return byTier.computeIfAbsent(tier, key -> new ConcurrentLinkedDeque<>());
	}

	private record CachedDrill(String text, Instant generatedAt) {
	}
}
