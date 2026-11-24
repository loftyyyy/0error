package com.rho.backend.drills;

import java.util.List;
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
	private final DrillIssuanceStore issuances;

	public DrillService(
			TierCatalogue catalogue,
			StaticDrillPool pool,
			DrillValidator validator,
			AiDrillCache aiDrills,
			TierProgressService progress,
			WeakPairService weakPairs,
			FingerStatService fingers,
			DrillIssuanceStore issuances) {
		this.catalogue = catalogue;
		this.pool = pool;
		this.validator = validator;
		this.aiDrills = aiDrills;
		this.progress = progress;
		this.weakPairs = weakPairs;
		this.fingers = fingers;
		this.issuances = issuances;
	}

	public Mono<DrillResponse> next(UUID userId, int tier, String exclude) {
		return next(userId, tier, null, exclude);
	}

	/**
	 * Serves one drill. A non-blank {@code focus} pair overrides the automatic
	 * weakness analysis, so the browser can offer "practise this pair" without
	 * waiting for two attempts to make it appear.
	 */
	public Mono<DrillResponse> next(UUID userId, int tier, String focus, String exclude) {
		return Mono.defer(() -> {
			TierDefinition definition = catalogue.require(tier);
			DrillFocus requested = explicitFocus(definition, focus);

			return progress.isUnlocked(userId, tier)
					.flatMap(unlocked -> {
						if (!unlocked) {
							return Mono.error(new ResponseStatusException(
									HttpStatus.FORBIDDEN, "Tier " + tier + " is locked"));
						}

						Mono<DrillFocus> chosen = requested != null
								? Mono.just(requested)
								: Mono.zip(
												weakPairs.focus(userId, tier),
												fingers.focus(userId, tier))
										.map(analysis -> new DrillFocus(
												analysis.getT1(),
												analysis.getT1().isEmpty()
														? analysis.getT2().orElse(null)
														: null));

						return chosen.flatMap(target -> serve(userId, definition, target, exclude));
					});
		});
	}

	/** Null means no explicit focus; anything malformed is a client error. */
	private static DrillFocus explicitFocus(TierDefinition tier, String focus) {
		if (focus == null || focus.isBlank()) {
			return null;
		}
		if (focus.length() != 2) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "focus must be exactly two characters");
		}
		for (char character : focus.toCharArray()) {
			if (character < 0x20 || character > 0x7E || !tier.allows(character)) {
				throw new ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"focus must use only characters this tier allows");
			}
		}
		return DrillFocus.of(List.of(focus));
	}

	private Mono<DrillResponse> serve(
			UUID userId, TierDefinition definition, DrillFocus focus, String exclude) {
		Optional<String> generated = aiDrills.take(definition.tier(), focus, exclude)
				.filter(candidate -> validator.isValid(candidate, definition));

		String source = generated.isPresent() ? "AI" : "STATIC";
		String text = generated.orElseGet(() -> pool.pick(definition.tier(), focus, exclude));
		validator.validate(text, definition).ifPresent(error -> {
			throw new IllegalStateException("Configured drill is invalid: " + error);
		});

		// Keep the cache warm for the next request, never for this one.
		aiDrills.refill(definition, focus, text);

		return issuances.issue(userId, definition, text, source)
				.map(drillId -> new DrillResponse(
						drillId,
						definition.tier(),
						definition.name(),
						definition.charset(),
						source,
						text,
						text.length()));
	}
}
