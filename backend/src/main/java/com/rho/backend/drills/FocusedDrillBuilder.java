package com.rho.backend.drills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import com.rho.backend.analysis.Finger;

/**
 * Builds an offline drill from what a typist needs to practise. It is only used
 * when no curated drill covers a focus pair or reach, so the adaptive loop
 * still works without the AI generator.
 *
 * <p>Output is deterministic: target material first, then repeats with a short
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

	/**
	 * Returns a drill built only from the keys one finger reaches. Null when
	 * the tier allows none of them, for example the thumbs on a letter tier.
	 */
	static String buildForFinger(TierDefinition tier, Finger finger, String exclude) {
		List<String> keys = allowedKeys(tier, finger);
		if (keys.isEmpty()) {
			return null;
		}

		List<String> tokens = adjacentTokens(keys);
		String forward = compose(tier, tokens, false);
		if (!forward.equals(exclude)) {
			return forward;
		}

		List<String> reversed = new ArrayList<>(tokens);
		Collections.reverse(reversed);
		String backward = compose(tier, reversed, false);
		return backward.equals(exclude) ? null : backward;
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

	static List<String> allowedKeys(TierDefinition tier, Finger finger) {		LinkedHashSet<String> keys = new LinkedHashSet<>();
		for (char key : finger.baseKeys().toCharArray()) {
			// A space-only drill would not teach the reach itself.
			if (key != ' ' && tier.allows(key)) {
				keys.add(String.valueOf(key));
			}
		}
		return List.copyOf(keys);
	}

	private static List<String> adjacentTokens(List<String> keys) {
		if (keys.size() == 1) {
			return List.of(keys.get(0) + keys.get(0));
		}

		List<String> tokens = new ArrayList<>(keys.size());
		for (int index = 0; index < keys.size(); index++) {
			tokens.add(keys.get(index) + keys.get((index + 1) % keys.size()));
		}
		return tokens;
	}

	private static String compose(TierDefinition tier, List<String> tokens, boolean breakMonotony) {
		List<String> built = new ArrayList<>(tokens);
		int length = joinedLength(built);
		int next = 0;

		while (length < tier.minLength()) {
			boolean useBase = breakMonotony && built.size() % BASE_TOKEN_INTERVAL == BASE_TOKEN_INTERVAL - 1;
			String token = useBase ? baseToken(tier) : tokens.get(next++ % tokens.size());
			built.add(token);
			length += token.length() + 1;
		}

		while (length > tier.maxLength() && built.size() > 1) {
			String removed = built.remove(built.size() - 1);
			length -= removed.length() + 1;
		}

		return String.join(" ", built);
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
