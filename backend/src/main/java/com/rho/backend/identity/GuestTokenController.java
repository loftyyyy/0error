package com.rho.backend.identity;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class GuestTokenController {

	private final GuestTokenService guestTokens;

	private final GuestSignInLimiter signIns;

	public GuestTokenController(GuestTokenService guestTokens, GuestSignInLimiter signIns) {
		this.guestTokens = guestTokens;
		this.signIns = signIns;
	}

	@PostMapping("/guest")
	public Mono<ResponseEntity<GuestTokenResponse>> issueGuestToken(
			@RequestHeader(name = "X-Guest-Id", required = false) UUID guestId,
			@RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
			ServerWebExchange exchange) {
		if (GuestTokenService.isNewGuest(guestId, guestToken)
				&& !signIns.tryAcquire(clientAddress(exchange))) {
			return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.header(HttpHeaders.RETRY_AFTER, Long.toString(signIns.retryAfterSeconds()))
					.<GuestTokenResponse>build());
		}
		return guestTokens.issue(guestId, guestToken).map(ResponseEntity::ok);
	}

	/**
	 * The proxy address when one is in front. Counting it means a shared proxy
	 * pool shares one bucket, which is the safe direction for a guard.
	 */
	private static String clientAddress(ServerWebExchange exchange) {
		InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
		return remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
	}
}
