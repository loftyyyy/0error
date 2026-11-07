package com.rho.backend.telemetry;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface KeystrokeEventRepository extends ReactiveCrudRepository<KeystrokeEvent, Long> {

	Flux<KeystrokeEvent> findBySessionIdOrderBySeqAsc(UUID sessionId);

	Mono<Long> countBySessionId(UUID sessionId);

	Mono<Long> deleteBySessionId(UUID sessionId);
}
