package com.rho.backend.drills;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FocusedDrillBuilderTest {

	private final TierCatalogue catalogue = new TierCatalogue();
	private final DrillValidator validator = new DrillValidator();

	@Test
	void buildsAValidDrillCoveringTheFocusPairsForEveryTier() {
		Map<Integer, String> pairs = Map.of(
				0, "fa",
				1, "qg",
				2, "bn",
				3, "q[",
				4, "th");

		pairs.forEach((tierId, pair) -> {
			TierDefinition tier = catalogue.require(tierId);
			String drill = FocusedDrillBuilder.build(tier, List.of(pair), null);

			assertThat(drill).as("tier %d", tierId).contains(pair);
			assertThat(validator.validate(drill, tier))
					.as("tier %d drill %s", tierId, drill)
					.isEmpty();
		});
	}

	@Test
	void keepsEveryFocusPairAtTheStartAndDropsInvalidOnes() {
		TierDefinition homeRow = catalogue.require(0);

		String drill = FocusedDrillBuilder.build(
				homeRow, java.util.Arrays.asList("fa", "zz", "jd", null, "a"), null);

		assertThat(drill).contains("fa", "jd");
		assertThat(drill).doesNotContain("zz");
	}

	@Test
	void aRetryWithTheSameWeaknessGetsADifferentText() {
		TierDefinition homeRow = catalogue.require(0);

		String first = FocusedDrillBuilder.build(homeRow, List.of("fa"), null);
		String second = FocusedDrillBuilder.build(homeRow, List.of("fa"), first);

		assertThat(second).isNotNull().isNotEqualTo(first).contains("fa");
		assertThat(validator.validate(second, homeRow)).isEmpty();
	}

	@Test
	void unusableInputYieldsNoDrill() {
		TierDefinition homeRow = catalogue.require(0);

		assertThat(FocusedDrillBuilder.build(homeRow, List.of(), null)).isNull();
		assertThat(FocusedDrillBuilder.build(homeRow, null, null)).isNull();
		assertThat(FocusedDrillBuilder.build(homeRow, List.of("zz", "a"), null)).isNull();
	}
}
