package com.rho.backend.feedback;

import java.util.List;
import java.util.UUID;

import com.rho.backend.analysis.Finger;
import com.rho.backend.analysis.FingerStatService;
import com.rho.backend.analysis.WeakPairService;
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
	private final WeakPairService weakPairs;
	private final FingerStatService fingers;
	private final FeedbackGenerator generator;

	public FeedbackService(
			TypingSessionRepository sessions,
			TierCatalogue catalogue,
			WeakPairService weakPairs,
			FingerStatService fingers,
			FeedbackGenerator generator) {
		this.sessions = sessions;
		this.catalogue = catalogue;
		this.weakPairs = weakPairs;
		this.fingers = fingers;
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

					return Mono.zip(
									weakPairs.focus(userId, session.tier()),
									fingers.focus(userId, session.tier()))
							.flatMapMany(focus -> stream(
									session,
									focus.getT1(),
									focus.getT2().orElse(null)));
				}));
	}

	private Flux<String> stream(
			TypingSession session, List<String> focusPairs, Finger weakFinger) {
		FeedbackContext context = context(session, focusPairs, weakFinger);
		// The client concatenates tokens verbatim, so the deterministic
		// fallback is joined into one readable paragraph here.
		String fallback = String.join(" ", FallbackFeedback.messages(context));

		return generator.stream(context)
				.filter(token -> token != null && !token.isEmpty())
				.onErrorResume(error -> {
					log.warn("Feedback generation failed for session {}", session.id(), error);
					return Flux.just(" " + FallbackFeedback.closingLine());
				})
				.switchIfEmpty(Flux.just(fallback));
	}

	private FeedbackContext context(
			TypingSession session, List<String> focusPairs, Finger weakFinger) {
		return new FeedbackContext(
				session.tier(),
				catalogue.require(session.tier()).name(),
				session.keystrokeCount(),
				session.correctCount(),
				session.accuracy(),
				session.medianInterKeyMs(),
				session.p95InterKeyMs(),
				session.hesitationCount(),
				focusPairs,
				describeReach(weakFinger, session.allowedCharset()));
	}

	private static String describeReach(Finger finger, String allowedCharset) {
		if (finger == null) {
			return null;
		}

		StringBuilder keys = new StringBuilder();
		for (char key : finger.baseKeys().toCharArray()) {
			if (key != ' ' && allowedCharset.indexOf(key) >= 0) {
				keys.append(key);
			}
		}
		return keys.isEmpty() ? finger.label() : finger.label() + " (" + keys + ")";
	}
}
