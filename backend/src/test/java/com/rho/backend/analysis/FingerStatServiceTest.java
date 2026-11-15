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
class FingerStatServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();

	@Mock
	private FingerStatRepository repository;

	@Test
	void recordingMergesTheAnalyzedFingersForTheTier() {
		when(repository.merge(any(), anyInt(), any())).thenReturn(Mono.empty());
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true),
				key(1, 1, "s", false));

		service().recordSession(USER_ID, 2, events).block();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<FingerStat>> fingers = ArgumentCaptor.forClass(List.class);
		verify(repository).merge(eq(USER_ID), eq(2), fingers.capture());
		assertThat(fingers.getValue()).containsExactly(
				new FingerStat(Finger.LEFT_RING, 1, 1, 0, 100),
				new FingerStat(Finger.LEFT_PINKY, 1, 0, 0, 100));
	}

	@Test
	void reportOrdersTheWeakestReachesFirst() {
		when(repository.findByUserAndTier(USER_ID, 1)).thenReturn(Flux.just(
				new FingerStat(Finger.RIGHT_RING, 4, 0, 0, 1_200),
				new FingerStat(Finger.LEFT_RING, 4, 4, 0, 1_200)));

		FingerReport report = service().report(USER_ID, 1).block();

		assertThat(report).isNotNull();
		assertThat(report.tier()).isEqualTo(1);
		assertThat(report.fingers()).extracting(FingerReport.Entry::finger)
				.containsExactly("LEFT_RING", "RIGHT_RING");
		assertThat(report.fingers().get(0)).isEqualTo(
				new FingerReport.Entry("LEFT_RING", "Left ring", 4, 4, 0, 300, 0.55));
	}

	private FingerStatService service() {
		return new FingerStatService(repository);
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
