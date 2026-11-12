package com.rho.backend.drills;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DrillCatalogueTest {

	private final TierCatalogue catalogue = new TierCatalogue();
	private final StaticDrillPool pool = new StaticDrillPool();
	private final DrillValidator validator = new DrillValidator();

	@Test
	void everyStaticDrillFitsItsTierCharsetAndLength() {
		for (TierDefinition tier : catalogue.all()) {
			List<String> drills = pool.forTier(tier.tier());
			assertThat(drills).as("tier %d pool", tier.tier()).hasSizeGreaterThanOrEqualTo(3);

			for (String drill : drills) {
				assertThat(drill.length()).isBetween(tier.minLength(), tier.maxLength());
				assertThat(validator.validate(drill, tier))
						.as("tier %d drill %s", tier.tier(), drill)
						.isEmpty();
			}
		}
	}

	@Test
	void rejectsBadAiOutput() {
		TierDefinition homeRow = catalogue.require(0);

		assertThat(validator.validate("asdf", homeRow)).isPresent();
		assertThat(validator.validate("asdf jkl; asdf jkl; zzzz asdf jkl; asdf jkl;", homeRow)).isPresent();
		assertThat(validator.validate("asdf jkl; asdf jkl; fj fj dk dk sl sl a; a; é", homeRow)).isPresent();
	}

	@Test
	void exclusionAvoidsThePreviousDrill() {
		String first = pool.forTier(0).get(0);
		String next = pool.pick(0, first);

		assertThat(next).isNotEqualTo(first);
		assertThat(pool.forTier(0)).contains(next);
	}

	@Test
	void tierCatalogueCoversAllFiveTiers() {
		assertThat(catalogue.all()).extracting(TierDefinition::tier)
				.containsExactly(0, 1, 2, 3, 4);
		assertThat(catalogue.highestTier()).isEqualTo(4);
	}
}
