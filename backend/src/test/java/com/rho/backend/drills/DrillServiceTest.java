package com.rho.backend.drills;

import java.util.UUID;

import com.rho.backend.progress.TierProgressService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrillServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private TierProgressService progress;

	@Test
	void lockedTierIsForbidden() {
		when(progress.isUnlocked(USER_ID, 1)).thenReturn(Mono.just(false));

		StepVerifier.create(service().next(USER_ID, 1, null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.FORBIDDEN);
				})
				.verify();
	}

	@Test
	void unlockedTierReturnsAValidatedStaticDrill() {
		when(progress.isUnlocked(USER_ID, 0)).thenReturn(Mono.just(true));

		DrillResponse drill = service().next(USER_ID, 0, null).block();

		assertThat(drill).isNotNull();
		assertThat(drill.source()).isEqualTo("STATIC");
		assertThat(drill.text().length()).isBetween(40, 60);
		assertThat(drill.charset()).contains("a", "s", "d", "f", "j", "k", "l", ";", " ");
	}

	@Test
	void unknownTierIsBadRequest() {
		StepVerifier.create(service().next(USER_ID, 9, null))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.BAD_REQUEST);
				})
				.verify();
	}

	private DrillService service() {
		return new DrillService(
				new TierCatalogue(), new StaticDrillPool(), new DrillValidator(), progress);
	}
}
