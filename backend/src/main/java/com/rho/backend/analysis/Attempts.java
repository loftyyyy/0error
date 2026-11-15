package com.rho.backend.analysis;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.rho.backend.telemetry.KeystrokeEvent;
import com.rho.backend.telemetry.RawKeystroke;

/**
 * The counting rules shared by every analysis of one session. The first attempt
 * at a position defines what happened there: a correction never erases a
 * stumble. Warmup keys and backspaces are ignored.
 */
final class Attempts {

	private Attempts() {
	}

	static List<KeystrokeEvent> firstAttempts(List<KeystrokeEvent> events) {
		Map<Integer, KeystrokeEvent> firstAttempts = new TreeMap<>();
		for (KeystrokeEvent event : events) {
			if (!RawKeystroke.KEY.equals(event.kind()) || event.warmup()) {
				continue;
			}
			// Keep the earliest sequence for each position.
			firstAttempts.merge(event.position(), event,
					(kept, candidate) -> kept.seq() <= candidate.seq() ? kept : candidate);
		}
		return List.copyOf(firstAttempts.values());
	}

	static boolean isPrintableCharacter(String value) {
		if (value == null || value.length() != 1) {
			return false;
		}
		char character = value.charAt(0);
		// Matches the independent printable-ASCII guard in the aggregate schema.
		return character >= 0x20 && character <= 0x7E;
	}
}
