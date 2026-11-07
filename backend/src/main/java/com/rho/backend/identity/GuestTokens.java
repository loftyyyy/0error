package com.rho.backend.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque guest tokens. The raw token is returned once; only its SHA-256 hash is
 * persisted, so a database leak cannot be replayed as a login.
 */
public final class GuestTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final HexFormat HEX = HexFormat.of();

	private GuestTokens() {
	}

	public static String issue() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return "g_" + ENCODER.encodeToString(bytes);
	}

	public static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
