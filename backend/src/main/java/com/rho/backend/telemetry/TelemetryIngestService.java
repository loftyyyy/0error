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

		Set<Integer> sequences = new HashSet<>();
		for (RawKeystroke event : request.events()) {
			if (!sequences.add(event.seq())) {
				return Mono.error(new ResponseStatusException(
						HttpStatus.BAD_REQUEST, "Duplicate seq " + event.seq() + " in batch"));
			}
		}

		return sessions.findByIdAndUserId(sessionId, userId)
				.switchIfEmpty(Mono.error(new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Session not found")))
				.flatMap(session -> {
					// A closed session's aggregates were already judged by the
					// gate; late keystrokes would only make the stored numbers
					// disagree with the decision that was made from them.
					if (!"ACTIVE".equals(session.status())) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Session is already " + session.status()));
					}

					// Both the target character and correctness are derived from
					// the server-issued drill text, so a client cannot rewrite
					// what it was asked to type. The client's expected/correct
					// values are ignored here.
					List<RawKeystroke> events = request.events().stream()
							.map(event -> withServerTarget(event, session.drillText()))
							.toList();

					return writer.insertIgnoringDuplicates(sessionId, events)
							.flatMap(inserted -> metrics.recompute(sessionId)
									.map(aggregates -> new TelemetryIngestResponse(
											sessionId,
											events.size(),
											inserted,
											events.size() - inserted)));
				});
	}

	/**
	 * A key is correct only when the actual character equals the character the
	 * server issued at that position. Backspaces have no target and are always
	 * correct; a position outside the drill text cannot be correct. Sessions
	 * created before drill text was persisted fall back to the client's value.
	 */
	private static RawKeystroke withServerTarget(RawKeystroke event, String drillText) {
		String expected = drillText == null
				? event.expected()
				: expectedAt(drillText, event.position());
		boolean correct = event.isBackspace()
				|| (expected != null && expected.equals(event.actual()));

		return new RawKeystroke(
				event.seq(),
				event.position(),
				event.kind(),
				expected,
				event.actual(),
				correct,
				event.t(),
				event.displayMs(),
				event.warmup());
	}

	private static String expectedAt(String drillText, int position) {
		if (position < 0 || position >= drillText.length()) {
			return null;
		}
		return String.valueOf(drillText.charAt(position));
	}
}
