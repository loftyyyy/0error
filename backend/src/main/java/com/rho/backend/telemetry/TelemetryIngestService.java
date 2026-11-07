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

		List<RawKeystroke> events = request.events();
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
}
