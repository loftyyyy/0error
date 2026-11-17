package com.rho.backend.drills;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The issuance window is what makes the served text authoritative. These cases
 * are pure decisions over stored rows, so they run without a database; the
 * Postgres-backed acceptance test covers the claimed path end to end.
 */
@ExtendWith(MockitoExtension.class)
class DrillIssuanceStoreTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID OTHER_USER = UUID.randomUUID();
	private static final UUID DRILL_ID = UUID.randomUUID();

	@Mock
	private DrillIssuanceRepository issuances;

	@Mock
	private DatabaseClient databaseClient;

	@Test
	void foreignIssuanceIsRefused() {
		DrillIssuance foreign = issuance(
				DRILL_ID, OTHER_USER, OffsetDateTime.now().plusMinutes(5), 0);

		when(issuances.findAllById(List.of(DRILL_ID))).thenReturn(Flux.just(foreign));

		StepVerifier.create(store().load(USER_ID, List.of(DRILL_ID)))
				.expectErrorSatisfies(error -> assertBadRequest(error,
						"A drill in this set is missing, already used, or expired"))
				.verify();

		// Nothing is burned when the set is refused.
		verifyNoInteractions(databaseClient);
	}

	@Test
	void expiredIssuanceIsRefused() {
		DrillIssuance expired = issuance(
				DRILL_ID, USER_ID, OffsetDateTime.now().minusSeconds(1), 0);

		when(issuances.findAllById(List.of(DRILL_ID))).thenReturn(Flux.just(expired));

		StepVerifier.create(store().load(USER_ID, List.of(DRILL_ID)))
				.expectErrorSatisfies(error -> assertBadRequest(error,
						"A drill in this set is missing, already used, or expired"))
				.verify();

		verifyNoInteractions(databaseClient);
	}

	@Test
	void duplicateIdsAreRefusedBeforeAnyLookup() {
		StepVerifier.create(store().load(USER_ID, List.of(DRILL_ID, DRILL_ID)))
				.expectErrorSatisfies(error -> assertBadRequest(error,
						"A drill set must contain distinct drill ids"))
				.verify();

		verifyNoInteractions(issuances, databaseClient);
	}

	@Test
	void mixedTiersAreRefused() {
		UUID secondId = UUID.randomUUID();
		DrillIssuance first = issuance(
				DRILL_ID, USER_ID, OffsetDateTime.now().plusMinutes(5), 0);
		DrillIssuance second = issuance(
				secondId, USER_ID, OffsetDateTime.now().plusMinutes(5), 1);

		when(issuances.findAllById(List.of(DRILL_ID, secondId)))
				.thenReturn(Flux.just(first, second));

		StepVerifier.create(store().load(USER_ID, List.of(DRILL_ID, secondId)))
				.expectErrorSatisfies(error -> assertBadRequest(error,
						"A drill set must stay inside one tier"))
				.verify();

		verifyNoInteractions(databaseClient);
	}

	private DrillIssuanceStore store() {
		return new DrillIssuanceStore(issuances, databaseClient);
	}

	private static DrillIssuance issuance(
			UUID id, UUID owner, OffsetDateTime expiresAt, int tier) {
		return new DrillIssuance(
				id,
				owner,
				tier,
				"STATIC",
				"asdfjkl; ",
				"asdf jkl; asdf jkl; asdf jkl; asdf jkl;",
				OffsetDateTime.now(),
				expiresAt,
				null);
	}

	private static void assertBadRequest(Throwable error, String reason) {
		assertThat(error).isInstanceOf(ResponseStatusException.class);
		ResponseStatusException status = (ResponseStatusException) error;
		assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(status.getReason()).isEqualTo(reason);
	}
}
