package com.rho.backend.drills;

import java.util.List;

import com.rho.backend.analysis.Finger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticDrillPoolTest {

	private final TierCatalogue catalogue = new TierCatalogue();
	private final StaticDrillPool pool = new StaticDrillPool(catalogue);
	private final DrillValidator validator = new DrillValidator();

	@Test
	void prefersTheOnlyDrillThatCoversAFocusPair() {
		String picked = pool.pick(2, List.of("bn"), null);

		assertThat(picked).contains("bn");
		assertThat(picked).isEqualTo(pool.forTier(2).get(1));
	}

	@Test
	void aFocusPairStillHonoursTheExclude() {
		String excluded = pool.forTier(2).get(1);

		for (int attempt = 0; attempt < 20; attempt++) {
			String picked = pool.pick(2, List.of("bn"), excluded);
			assertThat(picked).isNotEqualTo(excluded).contains("bn");
		}
	}

	@Test
	void anUncoveredFocusPairBecomesATargetedOfflineDrill() {
		String picked = pool.pick(0, List.of("jf"), null);

		assertThat(picked).contains("jf");
		assertThat(validator.validate(picked, catalogue.require(0))).isEmpty();
	}

	@Test
	void aPairOutsideTheTierFallsBackToTheCuratedPool() {
		String picked = pool.pick(0, List.of("zz"), null);

		assertThat(pool.forTier(0)).contains(picked);
	}

	@Test
	void missingFocusBehavesLikeThePlainPicker() {
		String picked = pool.pick(1, List.of(), null);

		assertThat(pool.forTier(1)).contains(picked);
	}

	@Test
	void prefersTheCuratedDrillThatExercisesTheWeakFingerMost() {
		List<String> rightPinky = List.of("p", ";", "/", "-", "=", "[", "]", "'");

		String picked = pool.pick(3, new DrillFocus(List.of(), Finger.RIGHT_PINKY), null);

		int pickedCount = count(picked, rightPinky);
		for (String candidate : pool.forTier(3)) {
			assertThat(pickedCount)
					.as("candidate %s", candidate)
					.isGreaterThanOrEqualTo(count(candidate, rightPinky));
		}
		assertThat(picked).isEqualTo(pool.forTier(3).get(1));
	}

	@Test
	void aFingerFocusStillHonoursTheExclude() {
		String excluded = pool.forTier(3).get(1);

		for (int attempt = 0; attempt < 20; attempt++) {
			String picked = pool.pick(
					3, new DrillFocus(List.of(), Finger.RIGHT_PINKY), excluded);
			assertThat(picked).isNotEqualTo(excluded);
			assertThat(validator.validate(picked, catalogue.require(3))).isEmpty();
		}
	}

	@Test
	void aFingerWithNoKeysInTheTierLeavesThePoolAlone() {
		String picked = pool.pick(0, new DrillFocus(List.of(), Finger.THUMBS), null);

		assertThat(pool.forTier(0)).contains(picked);
	}

	private static int count(String text, List<String> keys) {
		int count = 0;
		for (String key : keys) {
			for (int index = 0; index < text.length(); index++) {
				if (text.charAt(index) == key.charAt(0)) {
					count++;
				}
			}
		}
		return count;
	}
}
