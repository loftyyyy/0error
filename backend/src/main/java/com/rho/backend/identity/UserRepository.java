package com.rho.backend.identity;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, UUID> {

	Mono<User> findByGuestTokenHash(String guestTokenHash);
}
