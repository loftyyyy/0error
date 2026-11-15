package com.rho.backend.drills;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Builds an offline drill from the transitions a typist needs to practise. It
 * is only used when no curated drill covers a focus pair, so the adaptive loop
 * still works without the AI generator.
 *
 * <p>Output is deterministic: focus pairs first, then repeats with a short
 * base-row token every few slots to avoid a wall of one bigram.
 */
final class FocusedDrillBuilder {

	private static final int BASE_TOKEN_INTERVAL = 4;
	private static final int BASE_TOKEN_LENGTH = 4;

	private FocusedDrillBuilder() {
	}

	/** Returns null when no usable focus pair remains. */
	static String build(TierDefinition tier, List<String> focusPairs, String exclude) {
		List<String> pairs = sanitize(tier, focusPairs);
		if (pairs.isEmpty()) {
			return null;
		}

		String mixed = compose(tier, pairs, true);
		if (!mixed.equals(exclude)) {
			return mixed;
		}

		// A retry with the same weakness needs a different text.
		String plain = compose(tier, pairs, false);
		return plain.equals(exclude) ? null : plain;
	}

	private static List<String> sanitize(TierDefinition tier, List<String> focusPairs) {
		if (focusPairs == null) {
			return List.of();
		}

		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String pair : focusPairs) {
			if (pair == null || pair.length() != 2) {
				continue;
			}
			boolean allowed = pair.chars()
					.allMatch(character -> character >= 0x20
							&& character <= 0x7E
							&& tier.allows((char) character));
			if (allowed) {
				unique.add(pair);
			}
		}
		return List.copyOf(unique);
	}

	private static String compose(TierDefinition tier, List<String> pairs, boolean breakMonotony) {
		List<String> tokens = new ArrayList<>(pairs);
		int length = joinedLength(tokens);
		int nextPair = 0;

		while (length < tier.minLength()) {
			boolean useBase = breakMonotony && tokens.size() % BASE_TOKEN_INTERVAL == BASE_TOKEN_INTERVAL - 1;
			String token = useBase ? baseToken(tier) : pairs.get(nextPair++ % pairs.size());
			tokens.add(token);
			length += token.length() + 1;
		}

		while (length > tier.maxLength() && tokens.size() > 1) {
			String removed = tokens.remove(tokens.size() - 1);
			length -= removed.length() + 1;
		}

		return String.join(" ", tokens);
	}

	private static int joinedLength(List<String> tokens) {
		int length = tokens.size() - 1;
		for (String token : tokens) {
			length += token.length();
		}
		return length;
	}

	private static String baseToken(TierDefinition tier) {
		if (tier.tier() == 4) {
			return "the";
		}

		StringBuilder base = new StringBuilder(BASE_TOKEN_LENGTH);
		for (char character : tier.charset().toCharArray()) {
			if (base.length() == BASE_TOKEN_LENGTH) {
				break;
			}
			if (Character.isLetter(character)) {
				base.append(character);
			}
		}
		return base.length() == BASE_TOKEN_LENGTH ? base.toString() : "asdf";
	}
}
