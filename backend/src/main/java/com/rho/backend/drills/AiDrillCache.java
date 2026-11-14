package com.rho.backend.drills;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Small in-memory buffer of validated generated drills. A request takes one if
 * it is ready; otherwise the static pool answers and a refill happens in the
 * background. This keeps the LLM off the request path entirely.
 *
 * <p>Queues are keyed by tier and the focus pairs they were written for, so a
 * drill targeting one weakness is never served to a different one. The number
 * of keys is bounded and the least recently used key is evicted first.
 */
@Component
public class AiDrillCache {

	private static final Logger log = LoggerFactory.getLogger(AiDrillCache.class);

	private final Map<String, FocusQueue> byKey = new ConcurrentHashMap<>();

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

	public Optional<String> take(int tier, String exclude) {
		return take(tier, List.of(), exclude);
	}

	/** Takes the oldest usable drill for this tier and focus set. */
	public Optional<String> take(int tier, List<String> focusPairs, String exclude) {
		FocusQueue queue = byKey.get(cacheKey(tier, focusPairs));
		if (queue == null) {
			return Optional.empty();
		}
		queue.touch();

		Instant cutoff = Instant.now().minus(Duration.ofMinutes(properties.ttlMinutes()));
		CachedDrill candidate;
		while ((candidate = queue.drills.pollFirst()) != null) {
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

	public void refill(TierDefinition tier, String exclude) {
		refill(tier, List.of(), exclude);
	}

	/**
	 * Starts at most one background generation per tier and focus set. Invalid
	 * output is discarded; a failure only means the static pool keeps serving.
	 */
	public void refill(TierDefinition tier, List<String> focusPairs, String exclude) {
		if (!properties.enabled()) {
			return;
		}

		List<String> focus = focusPairs == null ? List.of() : List.copyOf(focusPairs);
		String key = cacheKey(tier.tier(), focus);
		FocusQueue queue = queue(key);
		if (queue.drills.size() >= properties.cacheSize()) {
			return;
		}
		if (!queue.generating.compareAndSet(false, true)) {
			return;
		}

		generator.generate(tier, focus, exclude)
				.filter(text -> validator.isValid(text, tier))
				.doOnNext(text -> store(key, text))
				.doOnError(error -> log.warn("Drill generation failed for tier {}", tier.tier(), error))
				.onErrorResume(error -> Mono.empty())
				.doFinally(signal -> queue.generating.set(false))
				.subscribe();
	}

	/** Cached drills for a tier across every focus key. */
	int size(int tier) {
		String prefix = tier + "|";
		return byKey.entrySet().stream()
				.filter(entry -> entry.getKey().startsWith(prefix))
				.mapToInt(entry -> entry.getValue().drills.size())
				.sum();
	}

	int size(int tier, List<String> focusPairs) {
		FocusQueue queue = byKey.get(cacheKey(tier, focusPairs));
		return queue == null ? 0 : queue.drills.size();
	}

	int keyCount() {
		return byKey.size();
	}

	/** Stable across focus ordering: focus sets are sorted before encoding. */
	static String cacheKey(int tier, List<String> focusPairs) {
		String signature = focusPairs == null ? "" : focusPairs.stream()
				.filter(pair -> pair != null && pair.length() == 2)
				.distinct()
				.sorted()
				.map(AiDrillCache::encodePair)
				.collect(Collectors.joining(","));
		return tier + "|" + signature;
	}

	private static String encodePair(String pair) {
		return "%04x-%04x".formatted((int) pair.charAt(0), (int) pair.charAt(1));
	}

	private void store(String key, String text) {
		FocusQueue queue = queue(key);
		queue.drills.addLast(new CachedDrill(text, Instant.now()));

		// A slow burst of prefetches must not grow one queue without bound.
		while (queue.drills.size() > properties.cacheSize() + 2) {
			queue.drills.pollFirst();
		}
	}

	private FocusQueue queue(String key) {
		FocusQueue existing = byKey.get(key);
		if (existing != null) {
			existing.touch();
			return existing;
		}

		synchronized (byKey) {
			FocusQueue created = byKey.computeIfAbsent(key, ignored -> new FocusQueue());
			created.touch();
			evictEldestBeyondLimit(key);
			return created;
		}
	}

	private void evictEldestBeyondLimit(String keep) {
		while (byKey.size() > properties.maxFocusKeys()) {
			Optional<String> eldest = byKey.entrySet().stream()
					.filter(entry -> !entry.getKey().equals(keep))
					.min(Comparator.comparingLong(entry -> entry.getValue().lastUsedNanos()))
					.map(Map.Entry::getKey);
			if (eldest.isEmpty()) {
				return;
			}
			byKey.remove(eldest.get());
		}
	}

	private static final class FocusQueue {

		private final ConcurrentLinkedDeque<CachedDrill> drills = new ConcurrentLinkedDeque<>();
		private final AtomicBoolean generating = new AtomicBoolean();
		private volatile long lastUsedNanos = System.nanoTime();

		private void touch() {
			lastUsedNanos = System.nanoTime();
		}

		private long lastUsedNanos() {
			return lastUsedNanos;
		}
	}

	private record CachedDrill(String text, Instant generatedAt) {
	}
}
