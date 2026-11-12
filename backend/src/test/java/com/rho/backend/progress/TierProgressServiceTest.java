package com.rho.backend.progress;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.telemetry.SessionMetrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TierProgressServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private TierProgressStore store;

	private TierProgressService service() {
		return new TierProgressService(store, new TierCatalogue());
	}

	@Test
	void firstPassExtendsTheStreakWithoutUnlocking() {
		when(store.find(USER_ID, 0)).thenReturn(Mono.empty());
		when(store.save(any())).thenReturn(Mono.empty());

		GateOutcome outcome = service().applyCompletion(USER_ID, 0, passingMetrics()).block();

		assertThat(outcome).isNotNull();
		assertThat(outcome.passed()).isTrue();
		assertThat(outcome.progress().consecutivePasses()).isEqualTo(1);
		assertThat(outcome.progress().unlocked()).isTrue();
		assertThat(outcome.unlockedTier()).isNull();
		verify(store, never()).unlock(any(), eq(1), any());
	}

	@Test
	void secondConsecutivePassUnlocksTheNextTier() {
		when(store.find(USER_ID, 0)).thenReturn(Mono.just(
				new TierProgress(USER_ID, 0, 1, 0, OffsetDateTime.now())));
		when(store.find(USER_ID, 1)).thenReturn(Mono.empty());
		when(store.save(any())).thenReturn(Mono.empty());
		when(store.unlock(any(), eq(1), any())).thenReturn(Mono.empty());

		GateOutcome outcome = service().applyCompletion(USER_ID, 0, passingMetrics()).block();

		assertThat(outcome).isNotNull();
		assertThat(outcome.progress().consecutivePasses()).isEqualTo(2);
		assertThat(outcome.unlockedTier()).isEqualTo(1);
		verify(store).unlock(eq(USER_ID), eq(1), any());
	}

	@Test
	void failureResetsTheStreakAndCountsTowardRelaxation() {
		when(store.find(USER_ID, 0)).thenReturn(Mono.just(
				new TierProgress(USER_ID, 0, 1, 2, OffsetDateTime.now())));
		when(store.save(any())).thenReturn(Mono.empty());

		GateOutcome outcome = service().applyCompletion(USER_ID, 0, failingMetrics()).block();

		assertThat(outcome).isNotNull();
		assertThat(outcome.passed()).isFalse();
		assertThat(outcome.progress().consecutivePasses()).isZero();
		assertThat(outcome.progress().failedAttempts()).isEqualTo(3);
		assertThat(outcome.gate().medianThresholdMs()).isEqualTo(383);

		ArgumentCaptor<TierProgress> saved = ArgumentCaptor.forClass(TierProgress.class);
		verify(store).save(saved.capture());
		assertThat(saved.getValue().unlockedAt()).isNotNull();
	}

	@Test
	void alreadyUnlockedNextTierIsNotReportedAgain() {
		when(store.find(USER_ID, 0)).thenReturn(Mono.just(
				new TierProgress(USER_ID, 0, 2, 0, OffsetDateTime.now())));
		when(store.find(USER_ID, 1)).thenReturn(Mono.just(
				new TierProgress(USER_ID, 1, 0, 0, OffsetDateTime.now())));
		when(store.save(any())).thenReturn(Mono.empty());

		GateOutcome outcome = service().applyCompletion(USER_ID, 0, passingMetrics()).block();

		assertThat(outcome).isNotNull();
		assertThat(outcome.unlockedTier()).isNull();
		verify(store, never()).unlock(any(), eq(1), any());
	}

	private static SessionMetrics passingMetrics() {
		return new SessionMetrics(150, 150, new BigDecimal("99.00"), 300, 600, 0);
	}

	private static SessionMetrics failingMetrics() {
		return new SessionMetrics(80, 60, new BigDecimal("75.00"), 700, 1_200, 2);
	}
}
