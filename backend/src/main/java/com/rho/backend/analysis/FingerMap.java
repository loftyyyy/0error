package com.rho.backend.analysis;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a character to the finger that types it on a US keyboard, including the
 * shifted symbol above each key. A character with no key, such as a newline or
 * a non-ASCII glyph, maps to null and is left out of the analysis.
 */
final class FingerMap {

	private static final Map<Character, Finger> BY_CHARACTER = build();

	private FingerMap() {
	}

	static Finger fingerOf(String expected) {
		if (expected == null || expected.length() != 1) {
			return null;
		}
		return BY_CHARACTER.get(expected.charAt(0));
	}

	private static Map<Character, Finger> build() {
		Map<Character, Finger> map = new HashMap<>();
		assign(map, Finger.LEFT_PINKY, "`~1!qQaAzZ");
		assign(map, Finger.LEFT_RING, "2@wWsSxX");
		assign(map, Finger.LEFT_MIDDLE, "3#eEdDcC");
		assign(map, Finger.LEFT_INDEX, "4$5%rRtTfFgGvVbB");
		assign(map, Finger.THUMBS, " ");
		assign(map, Finger.RIGHT_INDEX, "6^7&yYuUhHjJnNmM");
		assign(map, Finger.RIGHT_MIDDLE, "8*iIkK,<");
		assign(map, Finger.RIGHT_RING, "9(oOlL.>");
		assign(map, Finger.RIGHT_PINKY, "0)pP;:/?-_[{=+]}\\\"'");
		return Map.copyOf(map);
	}

	private static void assign(Map<Character, Finger> map, Finger finger, String characters) {
		for (char character : characters.toCharArray()) {
			Finger previous = map.put(character, finger);
			if (previous != null) {
				throw new IllegalStateException("Character '" + character + "' has two fingers");
			}
		}
	}
}
