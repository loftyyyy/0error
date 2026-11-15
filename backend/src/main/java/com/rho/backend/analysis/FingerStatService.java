package com.rho.backend.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Owns per-finger aggregates: records them at completion and reports the
 * weakest reaches to the client.
 */
@Service
public class FingerStatService {

	private final FingerStatRepository repository;

	public FingerStatService(FingerStatRepository repository) {
		this.repository = repository;
	}

	/** Merges this session's finger counters inside the completion transaction. */
	public Mono<Void> recordSession(UUID userId, int tier, List<KeystrokeEvent> events) {
		return repository.merge(userId, tier, FingerAnalyzer.analyze(events));
	}

	public Mono<FingerReport> report(UUID userId, int tier) {
		return repository.findByUserAndTier(userId, tier)
				.collectList()
				.map(stats -> new FingerReport(tier, stats.stream()
						.sorted(Comparator.comparingDouble(FingerStat::score).reversed()
								.thenComparing(FingerStat::finger))
						.map(FingerReport.Entry::from)
						.toList()));
	}
}
