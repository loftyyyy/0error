package com.rho.backend.progress;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.drills.TierDefinition;
import com.rho.backend.telemetry.SessionMetrics;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class TierProgressService {

	private final TierProgressStore store;
	private final TierCatalogue catalogue;

	public TierProgressService(TierProgressStore store, TierCatalogue catalogue) {
		this.store = store;
		this.catalogue = catalogue;
	}

	/** Tier 0 is always open; every other tier needs an unlocked_at row. */
	public Mono<Boolean> isUnlocked(UUID userId, int tier) {
		if (tier == 0) {
			return Mono.just(true);
		}
		return store.find(userId, tier)
				.map(TierProgress::unlocked)
				.defaultIfEmpty(false);
	}

	/**
	 * Applies one completed session to the gate. A pass extends the consecutive
	 * streak; two consecutive passes unlock the next tier. A failure resets the
	 * streak and counts toward the next 15% cadence relaxation.
	 */
	public Mono<GateOutcome> applyCompletion(UUID userId, int tier, SessionMetrics metrics) {
		OffsetDateTime now = OffsetDateTime.now();

		return store.find(userId, tier)
				.defaultIfEmpty(TierProgress.empty(userId, tier, now))
				.flatMap(current -> {
					TierGateResult gate = TierGate.evaluate(metrics, current.failedAttempts());

					int passes = gate.passed()
							? Math.min(current.consecutivePasses() + 1, TierGate.REQUIRED_CONSECUTIVE_PASSES)
							: 0;
					int failures = gate.passed() ? 0 : current.failedAttempts() + 1;

					OffsetDateTime unlockedAt = current.unlockedAt();
					if (unlockedAt == null && tier == 0) {
						unlockedAt = now;
					}

					TierProgress updated = new TierProgress(userId, tier, passes, failures, unlockedAt);
					TierGateResult nextGate = new TierGateResult(
							gate.passed(),
							gate.failures(),
							TierGate.relax(TierGate.BASE_MEDIAN_MS, failures),
							TierGate.relax(TierGate.BASE_P95_MS, failures),
							gate.accuracyThreshold(),
							gate.minimumKeystrokes(),
							gate.requiredConsecutivePasses());

					boolean canUnlockNext = gate.passed()
							&& passes >= TierGate.REQUIRED_CONSECUTIVE_PASSES
							&& tier < catalogue.highestTier();
					if (!canUnlockNext) {
						return store.save(updated)
								.thenReturn(new GateOutcome(updated, gate.passed(), null, nextGate));
					}

					int nextTier = tier + 1;
					return store.find(userId, nextTier)
							.map(TierProgress::unlocked)
							.defaultIfEmpty(false)
							.flatMap(alreadyUnlocked -> {
								Mono<Void> unlock = alreadyUnlocked
										? Mono.empty()
										: store.unlock(userId, nextTier, now);
								return store.save(updated)
										.then(unlock)
										.thenReturn(new GateOutcome(
												updated,
												true,
												alreadyUnlocked ? null : nextTier,
												nextGate));
							});
				});
	}

	public Mono<ProgressSummary> summary(UUID userId) {
		return store.findAll(userId)
				.collectMap(TierProgress::tier)
				.map(byTier -> new ProgressSummary(catalogue.all().stream()
						.map(definition -> toStatus(definition, byTier.get(definition.tier())))
						.toList()));
	}

	private static TierStatus toStatus(TierDefinition definition, TierProgress progress) {
		int failures = progress == null ? 0 : progress.failedAttempts();
		boolean unlocked = definition.tier() == 0 || (progress != null && progress.unlocked());

		return new TierStatus(
				definition.tier(),
				definition.name(),
				definition.charset(),
				unlocked,
				progress == null ? 0 : progress.consecutivePasses(),
				failures,
				TierGate.relax(TierGate.BASE_MEDIAN_MS, failures),
				TierGate.relax(TierGate.BASE_P95_MS, failures),
				progress == null ? null : progress.unlockedAt());
	}
}
