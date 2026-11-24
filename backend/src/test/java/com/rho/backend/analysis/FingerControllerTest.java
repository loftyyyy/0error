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

class FingerControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final FingerStatService fingers = mock(FingerStatService.class);
	private final FingerController controller = new FingerController(fingers, new TierCatalogue());

	@Test
	void aGuestWithoutATokenIsRejected() {
		StepVerifier.create(controller.report(0, null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.UNAUTHORIZED);
				})
				.verify();
		verifyNoInteractions(fingers);
	}

	@Test
	void anUnknownTierIsRejectedBeforeAnyRead() {
		assertThatThrownBy(() -> controller.report(9, PRINCIPAL))
				.isInstanceOf(ResponseStatusException.class)
				.satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
						.isEqualTo(HttpStatus.BAD_REQUEST));
		verifyNoInteractions(fingers);
	}

	@Test
	void theGuestsReportForTheTierIsReturned() {
		FingerReport expected = new FingerReport(2, List.of());
		when(fingers.report(USER_ID, 2)).thenReturn(Mono.just(expected));

		assertThat(controller.report(2, PRINCIPAL).block()).isEqualTo(expected);
		verify(fingers).report(USER_ID, 2);
	}

	@Test
	void theTrendForwardsThePairAndWindow() {
		FingerTrend expected = new FingerTrend(2, "LEFT_RING", "Left ring", List.of());
		when(fingers.trend(USER_ID, 2, "LEFT_RING", 4)).thenReturn(Mono.just(expected));

		assertThat(controller.trend(2, "LEFT_RING", 4, PRINCIPAL).block()).isEqualTo(expected);
		verify(fingers).trend(USER_ID, 2, "LEFT_RING", 4);
	}
}
