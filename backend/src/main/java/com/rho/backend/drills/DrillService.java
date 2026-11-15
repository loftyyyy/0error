package com.rho.backend.drills;

import java.util.Optional;
import java.util.UUID;

import com.rho.backend.analysis.FingerStatService;
import com.rho.backend.analysis.WeakPairService;
import com.rho.backend.progress.TierProgressService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class DrillService {

	private final TierCatalogue catalogue;
	private final StaticDrillPool pool;
	private final DrillValidator validator;
	private final AiDrillCache aiDrills;
	private final TierProgressService progress;
	private final WeakPairService weakPairs;
	private final FingerStatService fingers;

	public DrillService(
			TierCatalogue catalogue,
			StaticDrillPool pool,
			DrillValidator validator,
			AiDrillCache aiDrills,
			TierProgressService progress,
			WeakPairService weakPairs,
			FingerStatService fingers) {
		this.catalogue = catalogue;
		this.pool = pool;
		this.validator = validator;
		this.aiDrills = aiDrills;
		this.progress = progress;
		this.weakPairs = weakPairs;
		this.fingers = fingers;
	}

	public Mono<DrillResponse> next(UUID userId, int tier, String exclude) {
		return Mono.defer(() -> {
			TierDefinition definition = catalogue.require(tier);

			return progress.isUnlocked(userId, tier)
					.flatMap(unlocked -> {
						if (!unlocked) {
							return Mono.error(new ResponseStatusException(
									HttpStatus.FORBIDDEN, "Tier " + tier + " is locked"));
						}

						return Mono.zip(
										weakPairs.focus(userId, tier),
										fingers.focus(userId, tier))
								.flatMap(focus -> serve(
										definition,
										new DrillFocus(
												focus.getT1(),
												focus.getT1().isEmpty()
														? focus.getT2().orElse(null)
														: null),
										exclude));
					});
		});
	}

	private Mono<DrillResponse> serve(
			TierDefinition definition, DrillFocus focus, String exclude) {
		Optional<String> generated = aiDrills.take(definition.tier(), focus, exclude)
				.filter(candidate -> validator.isValid(candidate, definition));

		String source = generated.isPresent() ? "AI" : "STATIC";
		String text = generated.orElseGet(() -> pool.pick(definition.tier(), focus, exclude));
		validator.validate(text, definition).ifPresent(error -> {
			throw new IllegalStateException("Configured drill is invalid: " + error);
		});

		// Keep the cache warm for the next request, never for this one.
		aiDrills.refill(definition, focus, text);

		return Mono.just(new DrillResponse(
				definition.tier(),
				definition.name(),
				definition.charset(),
				source,
				text,
				text.length()));
	}
}
