package com.rho.backend.drills;

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
@RequestMapping("/api/v1/drills")
public class DrillController {

	private final DrillService drills;

	public DrillController(DrillService drills) {
		this.drills = drills;
	}

	@GetMapping("/next")
	public Mono<DrillResponse> next(
			@RequestParam(defaultValue = "0") int tier,
			@RequestParam(required = false) String exclude,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}
		return drills.next(principal.userId(), tier, exclude);
	}
}
