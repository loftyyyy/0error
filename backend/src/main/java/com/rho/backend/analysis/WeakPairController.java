package com.rho.backend.analysis;

import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.identity.GuestPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/progress")
public class WeakPairController {

	private final WeakPairService weakPairs;
	private final TierCatalogue catalogue;

	public WeakPairController(WeakPairService weakPairs, TierCatalogue catalogue) {
		this.weakPairs = weakPairs;
		this.catalogue = catalogue;
	}

	@GetMapping("/weak-pairs")
	public Mono<WeakPairReport> report(
			@RequestParam(defaultValue = "0") int tier,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}
		catalogue.require(tier);
		return weakPairs.report(principal.userId(), tier);
	}
}
