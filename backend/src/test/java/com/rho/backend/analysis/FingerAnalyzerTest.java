package com.rho.backend.analysis;

import java.util.List;
import java.util.UUID;

import com.rho.backend.telemetry.KeystrokeEvent;
import com.rho.backend.telemetry.RawKeystroke;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerAnalyzerTest {

	private static final UUID SESSION_ID = UUID.randomUUID();

	@Test
	void countsTheFirstAttemptPerPositionAgainstItsFinger() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 200),
				key(1, 1, "s", true, 200),
				backspace(2, 1),
				key(3, 1, "s", true, 100),
				key(4, 2, "d", false, 1_000),
				key(5, 3, "f", true, 400));

		List<FingerStat> stats = FingerAnalyzer.analyze(events);

		assertThat(stats).extracting(FingerStat::finger).containsExactly(
				Finger.LEFT_MIDDLE, Finger.LEFT_INDEX, Finger.LEFT_PINKY, Finger.LEFT_RING);
		assertThat(statFor(stats, Finger.LEFT_MIDDLE))
				.isEqualTo(new FingerStat(Finger.LEFT_MIDDLE, 1, 1, 1, 1_000));
		assertThat(statFor(stats, Finger.LEFT_RING))
				.isEqualTo(new FingerStat(Finger.LEFT_RING, 1, 0, 0, 200));
	}

	@Test
	void warmupsBackspacesAndUnmappedCharactersAreIgnored() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100, true),
				backspace(1, 0),
				key(2, 1, "d", true, 100),
				key(3, 2, "\n", true, 100),
				key(4, 3, "s", false, 700));

		List<FingerStat> stats = FingerAnalyzer.analyze(events);

		assertThat(stats).extracting(FingerStat::finger)
				.containsExactly(Finger.LEFT_RING, Finger.LEFT_MIDDLE);
		assertThat(statFor(stats, Finger.LEFT_RING).errors()).isEqualTo(1);
	}

	@Test
	void repeatedReachesAccumulate() {
		List<KeystrokeEvent> events = List.of(
				key(0, 0, "a", true, 100),
				key(1, 1, "a", false, 500),
				key(2, 2, "a", true, null));

		List<FingerStat> stats = FingerAnalyzer.analyze(events);

		// Each position counts once; the middle attempt has no display timing
		// but still counts toward attempts and errors.
		assertThat(statFor(stats, Finger.LEFT_PINKY))
				.isEqualTo(new FingerStat(Finger.LEFT_PINKY, 3, 1, 0, 600));
	}

	@Test
	void anEmptySessionHasNoFingers() {
		assertThat(FingerAnalyzer.analyze(List.of())).isEmpty();
	}

	private static FingerStat statFor(List<FingerStat> stats, Finger finger) {
		return stats.stream()
				.filter(stat -> stat.finger() == finger)
				.findFirst()
				.orElseThrow();
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
