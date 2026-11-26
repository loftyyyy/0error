package com.rho.backend.drills;

import java.util.List;

import com.rho.backend.analysis.Finger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrillFocusTest {

	@Test
	void anEmptyFocusAcceptsAnyDrill() {
		assertThat(DrillFocus.none().matches("asdf jkl;")).isTrue();
	}

	@Test
	void pairFocusRequiresEveryPairInTheText() {
		DrillFocus focus = DrillFocus.of(List.of("jk"));

		assertThat(focus.matches("asdf jkl; asdf jkl;")).isTrue();
		assertThat(focus.matches("asdf asdf asdf")).isFalse();

		DrillFocus twoPairs = DrillFocus.of(List.of("jk", "sd"));
		assertThat(twoPairs.matches("asdf jkl; asdf jkl;")).isTrue();
		assertThat(twoPairs.matches("jkl; jkl; jkl; jkl;")).isFalse();
	}

	@Test
	void fingerFocusRequiresOneOfItsKeys() {
		DrillFocus rightMiddle = new DrillFocus(List.of(), Finger.RIGHT_MIDDLE);

		assertThat(rightMiddle.matches("asdf jkl; asdf")).isTrue();
		assertThat(rightMiddle.matches("asdf asdf asdf")).isFalse();
	}
}
