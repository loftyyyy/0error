package com.rho.backend.drills;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Issues drill texts and lets exactly one session claim them.
 *
 * <p>A drill may only become a session while it is owned by the guest, still
 * unconsumed, and younger than the short issuance window. That window is what
 * makes the served text authoritative: the client cannot invent a tier, a
 * charset, a length, or an expected character for a session.</p>
 */
@Service
public class DrillIssuanceStore {

	/** Long enough for an unhurried reader, short enough to limit stale ids. */
	static final Duration ISSUANCE_TTL = Duration.ofMinutes(10);

	private final DrillIssuanceRepository issuances;
	private final DatabaseClient databaseClient;

	public DrillIssuanceStore(DrillIssuanceRepository issuances, DatabaseClient databaseClient) {
		this.issuances = issuances;
		this.databaseClient = databaseClient;
	}

	public Mono<UUID> issue(UUID userId, TierDefinition definition, String text, String source) {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();

		// An explicit insert, not repository save: the id is assigned up front,
		// which makes save() treat the row as an update.
		return databaseClient.sql("""
				INSERT INTO drill_issuances (
				    id, user_id, tier, source, charset, drill_text, expires_at
				)
				VALUES (:id, :userId, :tier, :source, :charset, :drillText, :expiresAt)
				""")
				.bind("id", id)
				.bind("userId", userId)
				.bind("tier", definition.tier())
				.bind("source", source)
				.bind("charset", definition.charset())
				.bind("drillText", text)
				.bind("expiresAt", now.plus(ISSUANCE_TTL))
				.fetch()
				.rowsUpdated()
				.thenReturn(id);
	}

	/**
	 * Loads the given issuances, in the requested order, and refuses the whole
	 * set when any id is missing, foreign, expired, reused, duplicated, or from
	 * another tier. Consumption is a separate step so the tier can be checked
	 * before anything is burned.
	 */
	public Mono<List<DrillIssuance>> load(UUID userId, List<UUID> ids) {
		if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
			return Mono.error(new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "A drill set must contain distinct drill ids"));
		}

		OffsetDateTime now = OffsetDateTime.now();
		return issuances.findAllById(ids)
				.filter(issuance -> issuance.userId().equals(userId))
				.filter(issuance -> issuance.consumedAt() == null)
				.filter(issuance -> issuance.expiresAt().isAfter(now))
				.collectList()
				.flatMap(found -> {
					if (found.size() != ids.size()) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.BAD_REQUEST,
								"A drill in this set is missing, already used, or expired"));
					}

					Map<UUID, DrillIssuance> byId = found.stream()
							.collect(Collectors.toMap(DrillIssuance::id, Function.identity()));
					List<DrillIssuance> ordered = ids.stream().map(byId::get).toList();

					int tier = ordered.get(0).tier();
					if (ordered.stream().anyMatch(issuance -> issuance.tier() != tier)) {
						return Mono.error(new ResponseStatusException(
								HttpStatus.BAD_REQUEST,
								"A drill set must stay inside one tier"));
					}

					return Mono.just(ordered);
				});
	}

	/**
	 * Conditional updates make the claim atomic: two concurrent sessions citing
	 * the same drill cannot both win.
	 */
	public Mono<Void> consume(List<DrillIssuance> ordered) {
		return Flux.fromIterable(ordered)
				.concatMap(issuance -> databaseClient.sql("""
						UPDATE drill_issuances
						SET consumed_at = now()
						WHERE id = :id AND consumed_at IS NULL
						""")
						.bind("id", issuance.id())
						.fetch()
						.rowsUpdated())
				.collectList()
				.flatMap(counts -> counts.stream().allMatch(count -> count == 1)
						? Mono.<Void>empty()
						: Mono.error(new ResponseStatusException(
								HttpStatus.CONFLICT, "A drill in this set was already used")));
	}

	/** Removes long-expired issuances; their only value was the short window. */
	public Mono<Long> deleteExpiredBefore(OffsetDateTime cutoff) {
		return databaseClient.sql("DELETE FROM drill_issuances WHERE expires_at < :cutoff")
				.bind("cutoff", cutoff)
				.fetch()
				.rowsUpdated();
	}
}
