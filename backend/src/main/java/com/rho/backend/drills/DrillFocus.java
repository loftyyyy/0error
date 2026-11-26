package com.rho.backend.drills;

import java.util.List;

import com.rho.backend.analysis.Finger;

/**
 * What the next drill should lean on. Weak transitions are more specific than
 * a weak finger, so pairs win when both are present. A null finger means the
 * analysis has nothing to target.
 */
public record DrillFocus(List<String> pairs, Finger finger) {

	public DrillFocus {
		pairs = pairs == null ? List.of() : List.copyOf(pairs);
	}

	public static DrillFocus none() {
		return new DrillFocus(List.of(), null);
	}

	public static DrillFocus of(List<String> pairs) {
		return new DrillFocus(pairs, null);
	}

	public boolean hasPairs() {
		return !pairs.isEmpty();
	}

	public boolean empty() {
		return pairs.isEmpty() && finger == null;
	}

	/**
	 * True when the drill actually exercises this focus: every requested pair is
	 * present, or at least one key the finger owns appears. No focus accepts all.
	 */
	public boolean matches(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		if (!pairs.isEmpty()) {
			return pairs.stream()
					.filter(pair -> pair != null && pair.length() == 2)
					.allMatch(text::contains);
		}
		if (finger != null) {
			for (char key : finger.baseKeys().toCharArray()) {
				if (key != ' ' && text.indexOf(key) >= 0) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
}
