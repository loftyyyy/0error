package com.rho.backend.feedback;

import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/sessions")
public class FeedbackController {

	private final FeedbackService feedback;

	public FeedbackController(FeedbackService feedback) {
		this.feedback = feedback;
	}

	@GetMapping(value = "/{sessionId}/feedback", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<String>> feedback(
			@PathVariable UUID sessionId,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Flux.error(new ResponseStatusException(
					HttpStatus.UNAUTHORIZED, "Guest token required"));
		}

		return feedback.stream(principal.userId(), sessionId)
				.map(token -> ServerSentEvent.<String>builder(token).event("token").build())
				.concatWithValues(ServerSentEvent.<String>builder("[DONE]").event("done").build());
	}
}
