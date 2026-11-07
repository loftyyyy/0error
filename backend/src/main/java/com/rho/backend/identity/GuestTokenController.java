package com.rho.backend.identity;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class GuestTokenController {

	private final GuestTokenService guestTokens;

	public GuestTokenController(GuestTokenService guestTokens) {
		this.guestTokens = guestTokens;
	}

	@PostMapping("/guest")
	public Mono<GuestTokenResponse> issueGuestToken(
			@RequestHeader(name = "X-Guest-Id", required = false) UUID guestId,
			@RequestHeader(name = "X-Guest-Token", required = false) String guestToken) {
		return guestTokens.issue(guestId, guestToken);
	}
}
