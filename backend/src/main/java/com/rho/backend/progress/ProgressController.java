package com.rho.backend.progress;

import com.rho.backend.identity.GuestPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/progress")
public class ProgressController {

	private final TierProgressService progress;

	public ProgressController(TierProgressService progress) {
		this.progress = progress;
	}

	@GetMapping
	public Mono<ProgressSummary> summary(@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}
		return progress.summary(principal.userId());
	}
}
