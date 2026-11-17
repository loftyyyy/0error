package com.rho.backend.drills;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DrillIssuanceRepository extends ReactiveCrudRepository<DrillIssuance, UUID> {
}
