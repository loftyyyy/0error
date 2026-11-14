package com.rho.backend.analysis;

import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;
import com.rho.backend.telemetry.RawKeystroke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeakPairServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();

	@Mock
	private WeakPairRepository repository;

	@Test
	void recordingMergesTheAnalyzedPairsForTheTier() {
		when(repository.merge(any(), anyInt(), any())).thenReturn(Mono.empty());
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true),
				key(1, 1, "s", false));

		service().recordSession(USER_ID, 2, events).block();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WeakPair>> pairs = ArgumentCaptor.forClass(List.class);
		verify(repository).merge(eq(USER_ID), eq(2), pairs.capture());
		assertThat(pairs.getValue()).containsExactly(new WeakPair("a", "s", 1, 1, 0, 100));
	}

	@Test
	void reportOrdersTheWeakestPairsFirst() {
		when(repository.findByUserAndTier(USER_ID, 1)).thenReturn(Flux.just(
				new WeakPair("l", ";", 4, 0, 0, 1_200),
				new WeakPair("a", "s", 4, 4, 0, 1_200)));

		WeakPairReport report = service().report(USER_ID, 1).block();

		assertThat(report).isNotNull();
		assertThat(report.tier()).isEqualTo(1);
		assertThat(report.pairs()).extracting(WeakPairReport.Pair::pair)
				.containsExactly("as", "l;");
		assertThat(report.pairs().get(0))
				.isEqualTo(new WeakPairReport.Pair("as", 4, 4, 0, 300, 0.55));
	}

	@Test
	void focusKeepsRepeatedHighScorePairsAndLimitsTheLessonToThree() {
		when(repository.findByUserAndTier(USER_ID, 0)).thenReturn(Flux.just(
				new WeakPair("a", "s", 4, 4, 0, 1_200),
				new WeakPair("d", "f", 4, 0, 4, 1_200),
				new WeakPair("j", "k", 4, 0, 0, 6_000),
				new WeakPair("s", "l", 1, 1, 0, 1_200),
				new WeakPair("l", ";", 4, 0, 0, 1_200)));

		List<String> focus = service().focus(USER_ID, 0).block();

		assertThat(focus).containsExactly("as", "df", "jk");
	}

	@Test
	void aFailingReadLeavesDrillsUntargeted() {
		when(repository.findByUserAndTier(USER_ID, 0))
				.thenReturn(Flux.error(new IllegalStateException("analysis unavailable")));

		assertThat(service().focus(USER_ID, 0).block()).isEqualTo(List.of());
	}

	private WeakPairService service() {
		return new WeakPairService(repository);
	}

	private static KeystrokeEvent key(int seq, int position, String expected, boolean correct) {
		return new KeystrokeEvent(
				null,
				SESSION_ID,
				seq,
				position,
				RawKeystroke.KEY,
				expected,
				correct ? expected : "x",
				correct,
				seq * 100,
				100,
				false,
				null);
	}
}
