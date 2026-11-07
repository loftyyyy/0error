package com.rho.backend.sessions;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface TypingSessionRepository extends ReactiveCrudRepository<TypingSession, UUID> {

	Mono<TypingSession> findByIdAndUserId(UUID id, UUID userId);

	Mono<TypingSession> findByUserIdAndClientSessionId(UUID userId, UUID clientSessionId);
}
