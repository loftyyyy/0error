package com.rho.backend.drills;

import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DrillControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final DrillService drills = mock(DrillService.class);
	private final DrillController controller = new DrillController(drills);

	@Test
	void aGuestWithoutATokenIsRejected() {
		StepVerifier.create(controller.next(0, null, null, null, null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.UNAUTHORIZED);
				})
				.verify();
		verifyNoInteractions(drills);
	}

	@Test
	void tierFocusAndExcludeReachTheService() {
		DrillResponse expected = new DrillResponse(
				UUID.randomUUID(), 2, "Top row", "qwerty", "AI", "qwer qwer", 9);
		when(drills.next(USER_ID, 2, "bn", null, "qwer qwer"))
				.thenReturn(Mono.just(expected));

		DrillResponse response = controller.next(2, "bn", null, "qwer qwer", PRINCIPAL).block();

		assertThat(response).isEqualTo(expected);
		verify(drills).next(USER_ID, 2, "bn", null, "qwer qwer");
	}

	@Test
	void aFingerFocusAlsoReachesTheService() {
		DrillResponse expected = new DrillResponse(
				UUID.randomUUID(), 1, "Top row", "qwerty", "STATIC", "qwer qwer", 9);
		when(drills.next(USER_ID, 1, null, "LEFT_RING", null))
				.thenReturn(Mono.just(expected));

		DrillResponse response = controller.next(1, null, "LEFT_RING", null, PRINCIPAL).block();

		assertThat(response).isEqualTo(expected);
		verify(drills).next(USER_ID, 1, null, "LEFT_RING", null);
	}
}
