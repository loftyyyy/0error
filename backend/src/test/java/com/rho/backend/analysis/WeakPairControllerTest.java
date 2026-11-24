package com.rho.backend.analysis;

import java.util.List;
import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.identity.GuestPrincipal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeakPairControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final WeakPairService weakPairs = mock(WeakPairService.class);
	private final WeakPairController controller = new WeakPairController(weakPairs, new TierCatalogue());

	@Test
	void aGuestWithoutATokenIsRejected() {
		StepVerifier.create(controller.report(0, null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.UNAUTHORIZED);
				})
				.verify();
		verifyNoInteractions(weakPairs);
	}

	@Test
	void anUnknownTierIsRejectedBeforeAnyRead() {
		assertThatThrownBy(() -> controller.trend(9, "bn", 5, PRINCIPAL))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
						.isEqualTo(HttpStatus.BAD_REQUEST));
		verifyNoInteractions(weakPairs);
	}

	@Test
	void theGuestsPairsForTheTierAreReturned() {
		WeakPairReport expected = new WeakPairReport(2, List.of());
		when(weakPairs.report(USER_ID, 2)).thenReturn(Mono.just(expected));

		assertThat(controller.report(2, PRINCIPAL).block()).isEqualTo(expected);
		verify(weakPairs).report(USER_ID, 2);
	}

	@Test
	void theTrendForwardsThePairAndWindow() {
		WeakPairTrend expected = new WeakPairTrend(2, "bn", List.of());
		when(weakPairs.trend(USER_ID, 2, "bn", 3)).thenReturn(Mono.just(expected));

		assertThat(controller.trend(2, "bn", 3, PRINCIPAL).block()).isEqualTo(expected);
		verify(weakPairs).trend(USER_ID, 2, "bn", 3);
	}
}
