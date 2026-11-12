package com.rho.backend.drills;

/**
 * The drill payload. {@code charset} is the tier charset the server validated
 * against, so the client can highlight only allowed keys.
 */
public record DrillResponse(
		int tier,
		String tierName,
		String charset,
		String source,
		String text,
		int length) {
}
