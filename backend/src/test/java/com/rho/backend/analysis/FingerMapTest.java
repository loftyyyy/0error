package com.rho.backend.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerMapTest {

	@Test
	void mapsLettersToTheirTouchTypingFinger() {
		assertThat(FingerMap.fingerOf("a")).isEqualTo(Finger.LEFT_PINKY);
		assertThat(FingerMap.fingerOf("s")).isEqualTo(Finger.LEFT_RING);
		assertThat(FingerMap.fingerOf("d")).isEqualTo(Finger.LEFT_MIDDLE);
		assertThat(FingerMap.fingerOf("f")).isEqualTo(Finger.LEFT_INDEX);
		assertThat(FingerMap.fingerOf("j")).isEqualTo(Finger.RIGHT_INDEX);
		assertThat(FingerMap.fingerOf("k")).isEqualTo(Finger.RIGHT_MIDDLE);
		assertThat(FingerMap.fingerOf("l")).isEqualTo(Finger.RIGHT_RING);
		assertThat(FingerMap.fingerOf(";")).isEqualTo(Finger.RIGHT_PINKY);
	}

	@Test
	void shiftedSymbolsUseTheBaseKeyFinger() {
		assertThat(FingerMap.fingerOf("A")).isEqualTo(Finger.LEFT_PINKY);
		assertThat(FingerMap.fingerOf("!")).isEqualTo(Finger.LEFT_PINKY);
		assertThat(FingerMap.fingerOf("?")).isEqualTo(Finger.RIGHT_PINKY);
		assertThat(FingerMap.fingerOf(":")).isEqualTo(Finger.RIGHT_PINKY);
		assertThat(FingerMap.fingerOf("\"")).isEqualTo(Finger.RIGHT_PINKY);
	}

	@Test
	void spaceBelongsToTheThumbs() {
		assertThat(FingerMap.fingerOf(" ")).isEqualTo(Finger.THUMBS);
	}

	@Test
	void charactersWithoutAKeyHaveNoFinger() {
		assertThat(FingerMap.fingerOf("\n")).isNull();
		assertThat(FingerMap.fingerOf("é")).isNull();
		assertThat(FingerMap.fingerOf("ab")).isNull();
		assertThat(FingerMap.fingerOf(null)).isNull();
	}
}
