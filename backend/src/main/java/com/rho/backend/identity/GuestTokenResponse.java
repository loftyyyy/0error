package com.rho.backend.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GuestTokenResponse(
		UUID guestId,
		String token,
		String tokenType,
		OffsetDateTime issuedAt) {
}
