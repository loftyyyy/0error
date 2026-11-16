package com.rho.backend.telemetry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.rho.backend.sessions.TypingSessionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class TelemetryIngestService {

	private final TypingSessionRepository sessions;
	private final KeystrokeWriter writer;
	private final SessionMetricsService metrics;

	public TelemetryIngestService(
			TypingSessionRepository sessions,
			KeystrokeWriter writer,
			SessionMetricsService metrics) {
		this.sessions = sessions;
		this.writer = writer;
		this.metrics = metrics;
	}

	public Mono<TelemetryIngestResponse> ingest(
			UUID userId, UUID sessionId, TelemetryBatchRequest request) {
		if (!sessionId.equals(request.sessionId())) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "sessionId in body does not match the path"));
		}

		// Correctness feeds accuracy, so it is re-derived from the characters
		// rather than taken from the client. Ignoring the flag here keeps every
		// stored row, retry, and later aggregate consistent with the server.
		List<RawKeystroke> events = request.events().stream()
				.map(TelemetryIngestService::withServerCorrectness)
				.toList();
		Set<Integer> sequences = new HashSet<>();
		for (RawKeystroke event : events) {
			if (!sequences.add(event.seq())) {
				return Mono.error(new ResponseStatusException(
						HttpStatus.BAD_REQUEST, "Duplicate seq " + event.seq() + " in batch"));
			}
		}

		return sessions.findByIdAndUserId(sessionId, userId)
				.switchIfEmpty(Mono.error(new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Session not found")))
				.flatMap(session -> writer.insertIgnoringDuplicates(sessionId, events))
				.flatMap(inserted -> metrics.recompute(sessionId)
						.map(aggregates -> new TelemetryIngestResponse(
								sessionId,
								events.size(),
								inserted,
								events.size() - inserted)));
	}

	/**
	 * A key is correct only when the actual character equals the expected one.
	 * Backspaces have no target and are always correct; a key without an
	 * expected character cannot be correct.
	 */
	private static RawKeystroke withServerCorrectness(RawKeystroke event) {
		boolean correct = event.isBackspace()
				|| (event.expected() != null && event.expected().equals(event.actual()));
		return new RawKeystroke(
				event.seq(),
				event.position(),
				event.kind(),
				event.expected(),
				event.actual(),
				correct,
				event.t(),
				event.displayMs(),
				event.warmup());
	}
}
