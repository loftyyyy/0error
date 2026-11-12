package com.rho.backend.drills;

import java.util.UUID;

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
	private final TierProgressService progress;

	public DrillService(
			TierCatalogue catalogue,
			StaticDrillPool pool,
			DrillValidator validator,
			TierProgressService progress) {
		this.catalogue = catalogue;
		this.pool = pool;
		this.validator = validator;
		this.progress = progress;
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

						String text = pool.pick(tier, exclude);
						validator.validate(text, definition).ifPresent(error -> {
							throw new IllegalStateException("Configured drill is invalid: " + error);
						});

						return Mono.just(new DrillResponse(
								tier,
								definition.name(),
								definition.charset(),
								"STATIC",
								text,
								text.length()));
					});
		});
	}
}
