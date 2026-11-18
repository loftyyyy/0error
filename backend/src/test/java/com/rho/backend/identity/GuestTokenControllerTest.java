package com.rho.backend.identity;

import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GuestTokenControllerTest {

	private static final String ADDRESS = "203.0.113.7";

	private static final UUID GUEST_ID = UUID.randomUUID();

	private static final String TOKEN = "g_fresh";

	private final GuestTokenService guestTokens = mock(GuestTokenService.class);

	private final GuestSignInLimiter signIns = mock(GuestSignInLimiter.class);

	@Test
	void aNewGuestOverTheLimitIsRejectedWithRetryAfter() {
		when(signIns.tryAcquire(ADDRESS)).thenReturn(false);
		when(signIns.retryAfterSeconds()).thenReturn(2L);

		StepVerifier.create(controller().issueGuestToken(null, null, exchange(ADDRESS)))
				.assertNext(response -> {
					assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
					assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
					assertThat(response.getBody()).isNull();
				})
				.verifyComplete();

		// Nothing is written for a request the limiter refuses.
		verifyNoInteractions(guestTokens);
	}

	@Test
	void anAdmittedNewGuestGetsAToken() {
		when(signIns.tryAcquire(ADDRESS)).thenReturn(true);
		when(guestTokens.issue(null, null)).thenReturn(Mono.just(response()));

		StepVerifier.create(controller().issueGuestToken(null, null, exchange(ADDRESS)))
				.assertNext(issued -> {
					assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
					assertThat(issued.getBody()).isEqualTo(response());
				})
				.verifyComplete();
	}

	@Test
	void rotatingAGuestIsNeverLimited() {
		when(guestTokens.issue(GUEST_ID, "g_old")).thenReturn(Mono.just(response()));

		StepVerifier.create(controller().issueGuestToken(GUEST_ID, "g_old", exchange(ADDRESS)))
				.assertNext(issued -> assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK))
				.verifyComplete();

		verify(signIns, never()).tryAcquire(anyString());
	}

	@Test
	void halfCredentialsAreNotCountedAsANewGuest() {
		when(guestTokens.issue(GUEST_ID, null))
				.thenReturn(Mono.error(new IllegalArgumentException("pair them")));

		StepVerifier.create(controller().issueGuestToken(GUEST_ID, null, exchange(ADDRESS)))
				.expectError(IllegalArgumentException.class)
				.verify();

		// The service decides how to refuse it; the limiter only guards creation.
		verifyNoInteractions(signIns);
	}

	private GuestTokenController controller() {
		return new GuestTokenController(guestTokens, signIns);
	}

	private static GuestTokenResponse response() {
		return new GuestTokenResponse(GUEST_ID, TOKEN, "Bearer", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
	}

	private static ServerWebExchange exchange(String address) {
		return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/guest")
				.remoteAddress(new InetSocketAddress(address, 43210))
				.build());
	}
}
