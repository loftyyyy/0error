package com.rho.backend.analysis;

import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;
import com.rho.backend.telemetry.RawKeystroke;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeakPairAnalyzerTest {

	private static final UUID SESSION_ID = UUID.randomUUID();

	@Test
	void countsConsecutivePairsAndScoresTheSecondKey() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100),
				key(1, 1, "s", true, 200),
				key(2, 2, "d", false, 1_000),
				key(3, 3, "f", true, 400));

		List<WeakPair> pairs = WeakPairAnalyzer.analyze(events);

		assertThat(pairs).containsExactly(
				new WeakPair("a", "s", 1, 0, 0, 200),
				new WeakPair("d", "f", 1, 0, 0, 400),
				new WeakPair("s", "d", 1, 1, 1, 1_000));
	}

	@Test
	void firstAttemptAtAPositionDefinesThePairAndCorrectionsDoNotPair() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100),
				key(1, 1, "s", false, 800),
				backspace(2, 1),
				key(3, 1, "s", true, 500),
				key(4, 2, "d", true, 300),
				// A jump across a skipped position must not invent a transition.
				key(5, 4, "f", true, 300));

		List<WeakPair> pairs = WeakPairAnalyzer.analyze(events);

		assertThat(pairs).containsExactly(
				new WeakPair("a", "s", 1, 1, 0, 800),
				new WeakPair("s", "d", 1, 0, 0, 300));
	}

	@Test
	void warmupKeysAndBackspacesAreIgnored() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100, true),
				key(1, 1, "s", true, 100, true),
				backspace(2, 1),
				key(3, 2, "d", true, 100),
				key(4, 3, "f", true, 100));

		assertThat(WeakPairAnalyzer.analyze(events))
				.containsExactly(new WeakPair("d", "f", 1, 0, 0, 100));
	}

	@Test
	void repeatedPairsAccumulateAndTimingCanBeMissing() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100),
				key(1, 1, "s", true, null),
				key(2, 2, "a", true, 100),
				key(3, 3, "s", false, 500));

		List<WeakPair> pairs = WeakPairAnalyzer.analyze(events);

		// "as" appears twice; only the second occurrence carries display timing.
		assertThat(pairs).containsExactly(
				new WeakPair("a", "s", 2, 1, 0, 500),
				new WeakPair("s", "a", 1, 0, 0, 100));
	}

	@Test
	void anEmptySessionHasNoPairs() {
		assertThat(WeakPairAnalyzer.analyze(List.of())).isEmpty();
	}

	@Test
	void nonPrintableExpectedCharactersCannotBreakTheSchemaGuard() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100),
				key(1, 1, "\n", true, 100),
				key(2, 2, "s", true, 100));

		assertThat(WeakPairAnalyzer.analyze(events)).isEmpty();
	}

	private static KeystrokeEvent key(
			int seq, int position, String expected, boolean correct, Integer displayMs) {
		return key(seq, position, expected, correct, displayMs, false);
	}

	private static KeystrokeEvent key(
			int seq,
			int position,
			String expected,
			boolean correct,
			Integer displayMs,
			boolean warmup) {
		return new KeystrokeEvent(
				null,
				SESSION_ID,
				seq,
				position,
				RawKeystroke.KEY,
				expected,
				correct ? expected : "x",
				correct,
				seq * 100,
				displayMs,
				warmup,
				null);
	}

	private static KeystrokeEvent backspace(int seq, int position) {
		return new KeystrokeEvent(
				null,
				SESSION_ID,
				seq,
				position,
				RawKeystroke.BACKSPACE,
				null,
				null,
				false,
				seq * 100,
				null,
				false,
				null);
	}
}
