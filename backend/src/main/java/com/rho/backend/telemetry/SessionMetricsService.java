package com.rho.backend.telemetry;

import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

/**
 * Recomputes the session aggregate columns from the stored raw events. The
 * client never supplies these numbers, so a tampered or buggy client cannot
 * inflate its tier progress.
 */
@Service
public class SessionMetricsService {

	private final KeystrokeEventRepository events;
	private final DatabaseClient databaseClient;

	public SessionMetricsService(KeystrokeEventRepository events, DatabaseClient databaseClient) {
		this.events = events;
		this.databaseClient = databaseClient;
	}

	public Mono<SessionMetrics> recompute(UUID sessionId) {
		return events.findBySessionIdOrderBySeqAsc(sessionId)
				.collectList()
				.map(SessionMetricsCalculator::calculate)
				.flatMap(metrics -> update(sessionId, metrics).thenReturn(metrics));
	}

	private Mono<Long> update(UUID sessionId, SessionMetrics metrics) {
		String sql = """
				UPDATE sessions
				SET keystroke_count = :keystrokes,
				    correct_count = :correct,
				    accuracy = :accuracy,
				    median_inter_key_ms = :median,
				    p95_inter_key_ms = :p95,
				    hesitation_count = :hesitations
				WHERE id = :sessionId
				""";

		DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
				.bind("keystrokes", metrics.keystrokeCount())
				.bind("correct", metrics.correctCount())
				.bind("hesitations", metrics.hesitationCount())
				.bind("sessionId", sessionId);

		spec = bindNullable(spec, "accuracy", metrics.accuracy(), java.math.BigDecimal.class);
		spec = bindNullable(spec, "median", metrics.medianInterKeyMs(), Integer.class);
		spec = bindNullable(spec, "p95", metrics.p95InterKeyMs(), Integer.class);

		return spec.fetch().rowsUpdated();
	}

	private static DatabaseClient.GenericExecuteSpec bindNullable(
			DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
		if (value == null) {
			return spec.bindNull(name, type);
		}
		return spec.bind(name, value);
	}
}
