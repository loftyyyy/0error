package com.rho.backend.drills;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class AiDrillCacheTest {

	private final TierCatalogue catalogue = new TierCatalogue();
	private final DrillValidator validator = new DrillValidator();
	private final TierDefinition homeRow = catalogue.require(0);
	private final List<String> staticDrills = new ArrayList<>(new StaticDrillPool().forTier(0));

	@Test
	void servesAValidatedGeneratedDrill() {
		AiDrillCache cache = cache(generator(staticDrills.get(0)), properties(2, 60));

		cache.refill(homeRow, null);

		assertThat(cache.take(0, null)).contains(staticDrills.get(0));
		assertThat(cache.size(0)).isZero();
	}

	@Test
	void discardsOutputThatBreaksTheTierCharset() {
		AiDrillCache cache = cache(generator("zzz qqq zzz qqq zzz qqq zzz qqq zzz qqq"), properties(2, 60));

		cache.refill(homeRow, null);

		assertThat(cache.take(0, null)).isEmpty();
		assertThat(cache.size(0)).isZero();
	}

	@Test
	void generationFailureLeavesTheCacheEmpty() {
		DrillGenerator failing = (tier, exclude) -> Mono.error(new IllegalStateException("boom"));
		AiDrillCache cache = cache(failing, properties(2, 60));

		cache.refill(homeRow, null);

		assertThat(cache.take(0, null)).isEmpty();
	}

	@Test
	void skippedDrillWhenItIsTheExcludedOne() {
		List<String> generated = new ArrayList<>(staticDrills);
		AiDrillCache cache = cache(generator(generated.toArray(String[]::new)), properties(2, 60));

		cache.refill(homeRow, null);
		cache.refill(homeRow, null);

		assertThat(cache.take(0, staticDrills.get(0))).contains(staticDrills.get(1));
	}

	@Test
	void disabledCacheNeverGenerates() {
		AtomicInteger calls = new AtomicInteger();
		DrillGenerator generator = (tier, exclude) -> {
			calls.incrementAndGet();
			return Mono.just(staticDrills.get(0));
		};
		AiDrillCache cache = cache(generator, new AiDrillProperties(false, 2, 60));

		cache.refill(homeRow, null);

		assertThat(calls).hasValue(0);
		assertThat(cache.take(0, null)).isEmpty();
	}

	@Test
	void keepsNoMoreThanTheConfiguredCacheSize() {
		AiDrillCache cache = cache(generator(staticDrills.toArray(String[]::new)), properties(1, 60));

		cache.refill(homeRow, null);
		cache.refill(homeRow, null);

		assertThat(cache.size(0)).isEqualTo(1);
	}

	private AiDrillCache cache(DrillGenerator generator, AiDrillProperties properties) {
		return new AiDrillCache(generator, validator, properties);
	}

	private static AiDrillProperties properties(int cacheSize, int ttlMinutes) {
		return new AiDrillProperties(true, cacheSize, ttlMinutes);
	}

	private static DrillGenerator generator(String... outputs) {
		List<String> queue = new ArrayList<>(List.of(outputs));
		return (tier, exclude) -> queue.isEmpty() ? Mono.empty() : Mono.just(queue.remove(0));
	}
}
