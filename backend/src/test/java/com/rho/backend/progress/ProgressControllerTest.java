package com.rho.backend.progress;

import java.util.List;
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

class ProgressControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final GuestPrincipal PRINCIPAL = new GuestPrincipal(USER_ID, "GUEST");

	private final TierProgressService progress = mock(TierProgressService.class);
	private final ProgressController controller = new ProgressController(progress);

	@Test
	void aGuestWithoutATokenIsRejected() {
		StepVerifier.create(controller.summary(null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.UNAUTHORIZED);
				})
				.verify();
		verifyNoInteractions(progress);
	}

	@Test
	void theGuestsOwnLadderIsReturned() {
		ProgressSummary expected = new ProgressSummary(List.of());
		when(progress.summary(USER_ID)).thenReturn(Mono.just(expected));

		assertThat(controller.summary(PRINCIPAL).block()).isEqualTo(expected);
		verify(progress).summary(USER_ID);
	}
}
