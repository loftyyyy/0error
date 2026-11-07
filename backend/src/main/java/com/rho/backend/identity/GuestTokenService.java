package com.rho.backend.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Service
public class GuestTokenService {

	private final UserRepository users;
	private final DatabaseClient databaseClient;

	public GuestTokenService(UserRepository users, DatabaseClient databaseClient) {
		this.users = users;
		this.databaseClient = databaseClient;
	}

	/**
	 * Creates a fresh guest when no credentials are supplied. Supplying both a
	 * guest id and its current token rotates the token for that same guest.
	 */
	public Mono<GuestTokenResponse> issue(UUID existingGuestId, String existingToken) {
		boolean hasId = existingGuestId != null;
		boolean hasToken = existingToken != null && !existingToken.isBlank();

		if (!hasId && !hasToken) {
			return create();
		}
		if (!hasId || !hasToken) {
			return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"X-Guest-Id and X-Guest-Token must be supplied together"));
		}
		return rotate(existingGuestId, existingToken);
	}

	private Mono<GuestTokenResponse> create() {
		String rawToken = GuestTokens.issue();
		OffsetDateTime now = OffsetDateTime.now();
		String sql = """
				INSERT INTO users (id, kind, guest_token_hash, created_at, updated_at)
				VALUES (:id, 'GUEST', :tokenHash, :now, :now)
				RETURNING id, created_at
				""";

		return databaseClient.sql(sql)
				.bind("id", UUID.randomUUID())
				.bind("tokenHash", GuestTokens.hash(rawToken))
				.bind("now", now)
				.map((row, metadata) -> new GuestTokenResponse(
						row.get("id", UUID.class),
						rawToken,
						"Bearer",
						row.get("created_at", OffsetDateTime.class)))
				.one();
	}

	private Mono<GuestTokenResponse> rotate(UUID guestId, String existingToken) {
		return users.findByGuestTokenHash(GuestTokens.hash(existingToken))
				.filter(user -> user.id().equals(guestId))
				.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
						"Invalid guest credentials")))
				.flatMap(user -> {
					String rawToken = GuestTokens.issue();
					String sql = """
							UPDATE users
							SET guest_token_hash = :tokenHash, updated_at = now()
							WHERE id = :id
							RETURNING id, created_at
							""";
					return databaseClient.sql(sql)
							.bind("tokenHash", GuestTokens.hash(rawToken))
							.bind("id", guestId)
							.map((row, metadata) -> new GuestTokenResponse(
									row.get("id", UUID.class),
									rawToken,
									"Bearer",
									OffsetDateTime.now()))
							.one();
				});
	}
}
