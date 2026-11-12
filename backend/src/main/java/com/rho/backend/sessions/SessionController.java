package com.rho.backend.sessions;

import java.util.UUID;

import com.rho.backend.identity.GuestPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

	private final SessionService sessions;
	private final SessionCompletionService completion;

	public SessionController(SessionService sessions, SessionCompletionService completion) {
		this.sessions = sessions;
		this.completion = completion;
	}

	@PostMapping
	public Mono<ResponseEntity<SessionResponse>> create(
			@Valid @RequestBody CreateSessionRequest request,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}

		return sessions.create(principal.userId(), request)
				.map(result -> ResponseEntity
						.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
						.body(SessionResponse.from(result.session())));
	}

	@PostMapping("/{sessionId}/complete")
	public Mono<SessionCompletionResponse> complete(
			@PathVariable UUID sessionId,
			@AuthenticationPrincipal GuestPrincipal principal) {
		if (principal == null) {
			return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Guest token required"));
		}
		return completion.complete(principal.userId(), sessionId);
	}
}
