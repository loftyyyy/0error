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
class WeakPairServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SESSION_ID = UUID.randomUUID();

	@Mock
	private WeakPairRepository repository;

	@Test
	void recordingMergesAndCopiesTheAnalyzedPairsForTheTier() {
		when(repository.merge(any(), anyInt(), any())).thenReturn(Mono.empty());
		when(repository.recordSession(any(), any(), anyInt(), any())).thenReturn(Mono.empty());
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true),
				key(1, 1, "s", false));

		service().recordSession(SESSION_ID, USER_ID, 2, events).block();

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WeakPair>> merged = ArgumentCaptor.forClass(List.class);
		verify(repository).merge(eq(USER_ID), eq(2), merged.capture());
		assertThat(merged.getValue()).containsExactly(new WeakPair("a", "s", 1, 1, 0, 100));

		// The same counts are kept once per session for the trend.
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WeakPair>> copied = ArgumentCaptor.forClass(List.class);
		verify(repository).recordSession(
				eq(SESSION_ID), eq(USER_ID), eq(2), copied.capture());
		assertThat(copied.getValue()).isEqualTo(merged.getValue());
	}

	@Test
	void trendReadsTheRecentAttemptsOldestFirst() {
		when(repository.findRecentAttempts(USER_ID, 2, "a", "s", 5)).thenReturn(Flux.just(
				WeakPairTrend.Attempt.from(OffsetDateTime.parse("2026-11-20T10:00:00Z"), 2, 0, 0, 600),
				WeakPairTrend.Attempt.from(OffsetDateTime.parse("2026-11-18T10:00:00Z"), 1, 1, 1, 900),
				WeakPairTrend.Attempt.from(OffsetDateTime.parse("2026-11-16T10:00:00Z"), 1, 0, 0, 1_200)));

		WeakPairTrend trend = service().trend(USER_ID, 2, "as", 5).block();

		assertThat(trend).isNotNull();
		assertThat(trend.pair()).isEqualTo("as");
		assertThat(trend.attempts()).extracting(WeakPairTrend.Attempt::meanMs)
				.containsExactly(1_200, 900, 300);
	}

	@Test
	void trendRejectsSomethingThatIsNotAPair() {
		StepVerifier.create(service().trend(USER_ID, 2, "abc", 5))
				.expectErrorSatisfies(error -> {
					assertThat(error).isInstanceOf(ResponseStatusException.class);
					assertThat(((ResponseStatusException) error).getStatusCode())
							.isEqualTo(HttpStatus.BAD_REQUEST);
				})
				.verify();
	}

	@Test
	void trendKeepsTheLimitInsideItsRange() {
		StepVerifier.create(service().trend(USER_ID, 2, "as", 0))
				.expectError(ResponseStatusException.class)
				.verify();
		StepVerifier.create(service().trend(USER_ID, 2, "as", 11))
				.expectError(ResponseStatusException.class)
				.verify();
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
