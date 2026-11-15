package com.rho.backend.drills;

import java.util.List;

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
}
