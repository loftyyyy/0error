package com.rho.backend.analysis;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a character to the finger that types it on a US keyboard, including the
 * shifted symbol above each key. A character with no key, such as a newline or
 * a non-ASCII glyph, maps to null and is left out of the analysis.
 */
final class FingerMap {

	private static final Map<Character, Character> SHIFTED = shifted();

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
		for (Finger finger : Finger.values()) {
			for (char base : finger.baseKeys().toCharArray()) {
				assign(map, base, finger);
				if (Character.isLetter(base)) {
					assign(map, Character.toUpperCase(base), finger);
				}
			}
		}
		SHIFTED.forEach((shifted, base) -> assign(map, shifted, map.get(base)));
		return Map.copyOf(map);
	}

	private static void assign(Map<Character, Finger> map, char character, Finger finger) {
		Finger previous = map.put(character, finger);
		if (previous != null) {
			throw new IllegalStateException("Character '" + character + "' has two fingers");
		}
	}

	/** Shifted symbols paired with the key they sit above. */
	private static Map<Character, Character> shifted() {
		return Map.ofEntries(
				Map.entry('~', '`'),
				Map.entry('!', '1'),
				Map.entry('@', '2'),
				Map.entry('#', '3'),
				Map.entry('$', '4'),
				Map.entry('%', '5'),
				Map.entry('^', '6'),
				Map.entry('&', '7'),
				Map.entry('*', '8'),
				Map.entry('(', '9'),
				Map.entry(')', '0'),
				Map.entry('_', '-'),
				Map.entry('+', '='),
				Map.entry('{', '['),
				Map.entry('}', ']'),
				Map.entry('|', '\\'),
				Map.entry(':', ';'),
				Map.entry('"', '\''),
				Map.entry('<', ','),
				Map.entry('>', '.'),
				Map.entry('?', '/'));
	}
}
