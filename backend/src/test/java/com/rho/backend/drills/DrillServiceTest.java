package com.rho.backend.drills;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rho.backend.analysis.WeakPairService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrillServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private TierProgressService progress;

	@Mock
	private AiDrillCache aiDrills;

	@Mock
	private WeakPairService weakPairs;

	@org.junit.jupiter.api.BeforeEach
	void noFocusByDefault() {
		lenient().when(weakPairs.focus(any(), anyInt())).thenReturn(Mono.just(List.of()));
	}

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
	void generatedDrillFromTheCacheIsServedAsAi() {
		when(progress.isUnlocked(USER_ID, 0)).thenReturn(Mono.just(true));
		String generated = "asdf jkl; asdf jkl; fj fj dk dk sl sl a; a; a;";
		when(aiDrills.take(0, List.of(), null)).thenReturn(Optional.of(generated));

		DrillResponse drill = service().next(USER_ID, 0, null).block();

		assertThat(drill).isNotNull();
		assertThat(drill.source()).isEqualTo("AI");
		assertThat(drill.text()).isEqualTo(generated);
		verify(aiDrills).refill(any(TierDefinition.class), eq(List.of()), eq(generated));
	}

	@Test
	void invalidGeneratedDrillFallsBackToStatic() {
		when(progress.isUnlocked(USER_ID, 0)).thenReturn(Mono.just(true));
		when(aiDrills.take(0, List.of(), null)).thenReturn(Optional.of("not a valid drill"));

		DrillResponse drill = service().next(USER_ID, 0, null).block();

		assertThat(drill).isNotNull();
		assertThat(drill.source()).isEqualTo("STATIC");
		assertThat(drill.text().length()).isBetween(40, 60);
	}

	@Test
	void focusPairsReachTheCacheAndItsRefill() {
		String targeted = "zxcv bn m, . zxcv bn m, . asdf jkl; qwer uiop zxcv";
		when(progress.isUnlocked(USER_ID, 2)).thenReturn(Mono.just(true));
		when(weakPairs.focus(USER_ID, 2)).thenReturn(Mono.just(List.of("bn")));
		when(aiDrills.take(2, List.of("bn"), null)).thenReturn(Optional.of(targeted));

		DrillResponse drill = service().next(USER_ID, 2, null).block();

		assertThat(drill).isNotNull();
		assertThat(drill.source()).isEqualTo("AI");
		assertThat(drill.text()).isEqualTo(targeted);
		verify(aiDrills).refill(any(TierDefinition.class), eq(List.of("bn")), eq(targeted));
	}

	@Test
	void staticDrillTargetsFocusWhenNoGeneratedDrillIsReady() {
		String targeted = "zxcv bn m, . zxcv bn m, . asdf jkl; qwer uiop zxcv";
		when(progress.isUnlocked(USER_ID, 2)).thenReturn(Mono.just(true));
		when(weakPairs.focus(USER_ID, 2)).thenReturn(Mono.just(List.of("bn")));
		when(aiDrills.take(2, List.of("bn"), null)).thenReturn(Optional.empty());

		DrillResponse drill = service().next(USER_ID, 2, null).block();

		assertThat(drill).isNotNull();
		assertThat(drill.source()).isEqualTo("STATIC");
		assertThat(drill.text()).isEqualTo(targeted);
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
				new TierCatalogue(),
				new StaticDrillPool(),
				new DrillValidator(),
				aiDrills,
				progress,
				weakPairs);
	}
}
