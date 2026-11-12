package com.rho.backend.feedback;

import java.util.UUID;

import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.sessions.TypingSession;
import com.rho.backend.sessions.TypingSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Coaching for a completed session. The stream always produces text: generated
 * tokens when available, deterministic sentences otherwise.
 */
@Service
public class FeedbackService {

	private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

	private final TypingSessionRepository sessions;
	private final TierCatalogue catalogue;
	private final FeedbackGenerator generator;

	public FeedbackService(
			TypingSessionRepository sessions, TierCatalogue catalogue, FeedbackGenerator generator) {
		this.sessions = sessions;
		this.catalogue = catalogue;
		this.generator = generator;
	}

	public Flux<String> stream(UUID userId, UUID sessionId) {
		return Flux.defer(() -> sessions.findByIdAndUserId(sessionId, userId)
				.switchIfEmpty(Mono.error(new ResponseStatusException(
						HttpStatus.NOT_FOUND, "Session not found")))
				.flatMapMany(session -> {
					if (!"COMPLETED".equals(session.status())) {
						return Flux.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "Session is not complete"));
					}

					FeedbackContext context = context(session);
					// The client concatenates tokens verbatim, so the deterministic
					// fallback is joined into one readable paragraph here.
					String fallback = String.join(" ", FallbackFeedback.messages(context));

					return generator.stream(context)
							.filter(token -> token != null && !token.isEmpty())
							.onErrorResume(error -> {
								log.warn("Feedback generation failed for session {}", sessionId, error);
								return Flux.just(" " + FallbackFeedback.closingLine());
							})
							.switchIfEmpty(Flux.just(fallback));
				}));
	}

	private FeedbackContext context(TypingSession session) {
		return new FeedbackContext(
				session.tier(),
				catalogue.require(session.tier()).name(),
				session.keystrokeCount(),
				session.correctCount(),
				session.accuracy(),
				session.medianInterKeyMs(),
				session.p95InterKeyMs(),
				session.hesitationCount());
	}
}
