package com.rho.backend.drills;

import java.util.UUID;

/**
 * The drill payload. {@code charset} is the tier charset the server validated
 * against, so the client can highlight only allowed keys. {@code drillId} is
 * the issuance a session must cite; the text itself is never accepted back.
 */
public record DrillResponse(
		UUID drillId,
		int tier,
		String tierName,
		String charset,
		String source,
		String text,
		int length) {
}
