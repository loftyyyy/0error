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
}
