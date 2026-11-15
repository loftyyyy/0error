package com.rho.backend.drills;

import java.util.List;

import com.rho.backend.analysis.Finger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiDrillGeneratorTest {

	@Test
	void stripsQuotesAndFenceWrappers() {
		assertThat(OpenAiDrillGenerator.clean(" \"asdf jkl;\"\n"))
				.isEqualTo("asdf jkl;");
		assertThat(OpenAiDrillGenerator.clean("```\nasdf jkl; fj dk\n```"))
				.isEqualTo("asdf jkl; fj dk");
		assertThat(OpenAiDrillGenerator.clean("```text\nasdf jkl;\n```"))
				.isEqualTo("asdf jkl;");
	}

	@Test
	void collapsesNewlinesIntoOneLine() {
		assertThat(OpenAiDrillGenerator.clean("asdf jkl;\nfj dk sl a;"))
				.isEqualTo("asdf jkl; fj dk sl a;");
	}

	@Test
	void emptyOutputStaysEmpty() {
		assertThat(OpenAiDrillGenerator.clean(null)).isNull();
		assertThat(OpenAiDrillGenerator.clean("  \n ")).isNull();
	}

	@Test
	void promptNamesTheFocusPairsOnlyWhenPresent() {
		TierDefinition tier = new TierCatalogue().require(2);

		String withFocus = OpenAiDrillGenerator.userPrompt(tier, List.of("bn", "m,"), null);
		String withoutFocus = OpenAiDrillGenerator.userPrompt(tier, List.of(), null);

		assertThat(withFocus).contains("bn, m,");
		assertThat(withoutFocus).doesNotContain("hesitates");
	}

	@Test
	void promptNamesTheWeakFingerAndTheKeysItReaches() {
		TierDefinition tier = new TierCatalogue().require(3);
		String prompt = OpenAiDrillGenerator.userPrompt(
				tier, new DrillFocus(List.of(), Finger.RIGHT_PINKY), null);

		assertThat(prompt).contains("right pinky", "p;/-=[]'");
		assertThat(prompt).doesNotContain("hesitates");
	}

	@Test
	void aPairFocusIsMoreSpecificThanAFinger() {
		TierDefinition tier = new TierCatalogue().require(3);
		String prompt = OpenAiDrillGenerator.userPrompt(
				tier, new DrillFocus(List.of("bn"), Finger.RIGHT_PINKY), null);

		assertThat(prompt).contains("bn").doesNotContain("right pinky");
	}
}
