package com.rho.backend.analysis;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;
import com.rho.backend.telemetry.RawKeystroke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
	void recordingMergesAndCopiesTheAnalyzedFingersForTheTier() {
		when(repository.merge(any(), anyInt(), any())).thenReturn(Mono.empty());
		when(repository.recordSession(any(), any(), anyInt(), any())).thenReturn(Mono.empty());
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true),
				key(1, 1, "s", false));

		service().recordSession(SESSION_ID, USER_ID, 2, events).block();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<FingerStat>> merged = ArgumentCaptor.forClass(List.class);
		verify(repository).merge(eq(USER_ID), eq(2), merged.capture());
		assertThat(merged.getValue()).containsExactly(
				new FingerStat(Finger.LEFT_RING, 1, 1, 0, 100),
				new FingerStat(Finger.LEFT_PINKY, 1, 0, 0, 100));

		// The same counters are kept once per session for the trend.
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<FingerStat>> copied = ArgumentCaptor.forClass(List.class);
		verify(repository).recordSession(
				eq(SESSION_ID), eq(USER_ID), eq(2), copied.capture());
		assertThat(copied.getValue()).isEqualTo(merged.getValue());
	}

	@Test
	void trendReadsTheRecentAttemptsOldestFirst() {
		when(repository.findRecentAttempts(USER_ID, 2, Finger.LEFT_RING, 5)).thenReturn(
				Flux.just(
						FingerTrend.Attempt.from(
								OffsetDateTime.parse("2026-11-20T10:00:00Z"), 2, 0, 0, 600),
						FingerTrend.Attempt.from(
								OffsetDateTime.parse("2026-11-18T10:00:00Z"), 1, 1, 1, 900),
						FingerTrend.Attempt.from(
								OffsetDateTime.parse("2026-11-16T10:00:00Z"), 1, 0, 0, 1_200)));

		FingerTrend trend = service().trend(USER_ID, 2, "LEFT_RING", 5).block();

		assertThat(trend).isNotNull();
		assertThat(trend.finger()).isEqualTo("LEFT_RING");
		assertThat(trend.label()).isEqualTo("Left ring");
		assertThat(trend.attempts()).extracting(FingerTrend.Attempt::meanMs)
				.containsExactly(1_200, 900, 300);
	}

	@Test
	void trendRejectsAnUnknownFinger() {
		StepVerifier.create(service().trend(USER_ID, 2, "LEFT_TOE", 5))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.BAD_REQUEST);
				})
				.verify();
	}

	@Test
	void trendKeepsTheLimitInsideItsRange() {
		StepVerifier.create(service().trend(USER_ID, 2, "LEFT_RING", 0))
				.expectError(ResponseStatusException.class)
				.verify();
		StepVerifier.create(service().trend(USER_ID, 2, "LEFT_RING", 11))
				.expectError(ResponseStatusException.class)
				.verify();
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

	@Test
	void focusPicksTheWeakestRepeatedReach() {
		when(repository.findByUserAndTier(USER_ID, 0)).thenReturn(Flux.just(
				new FingerStat(Finger.LEFT_PINKY, 4, 0, 0, 400),
				new FingerStat(Finger.RIGHT_RING, 4, 4, 0, 1_200),
				new FingerStat(Finger.LEFT_RING, 1, 1, 0, 1_200)));

		assertThat(service().focus(USER_ID, 0).block()).contains(Finger.RIGHT_RING);
	}

	@Test
	void focusIgnoresAReachBelowTheScoreFloor() {
		when(repository.findByUserAndTier(USER_ID, 0)).thenReturn(Flux.just(
				new FingerStat(Finger.LEFT_PINKY, 10, 0, 0, 250)));

		assertThat(service().focus(USER_ID, 0).block()).isEmpty();
	}

	@Test
	void aFailingReadLeavesDrillsUntargeted() {
		when(repository.findByUserAndTier(USER_ID, 0))
				.thenReturn(Flux.error(new IllegalStateException("analysis unavailable")));

		assertThat(service().focus(USER_ID, 0).block()).isEmpty();
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
