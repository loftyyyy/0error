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
public class FingerController {

	private final FingerStatService fingers;
	private final TierCatalogue catalogue;

	public FingerController(FingerStatService fingers, TierCatalogue catalogue) {
		this.fingers = fingers;
		this.catalogue = catalogue;
	}

	@GetMapping("/fingers")
	public Mono<FingerReport> report(
			@RequestParam(defaultValue = "0") int tier,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}
		catalogue.require(tier);
		return fingers.report(principal.userId(), tier);
	}
}
