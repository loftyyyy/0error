package com.rho.backend.feedback;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiFeedbackGeneratorTest {

	private static final FeedbackContext BASE = new FeedbackContext(
			2, "Bottom row", 140, 138, new BigDecimal("98.57"), 280, 620, 1,
			List.of(), null);

	@Test
	void promptCarriesTheFocusPairsAndTheWeakReach() {
		FeedbackContext focused = new FeedbackContext(
				2,
				"Bottom row",
				140,
				138,
				new BigDecimal("98.57"),
				280,
				620,
				1,
				List.of("bn", "m,"),
				"Right ring (ol.)");

		String prompt = OpenAiFeedbackGenerator.userPrompt(focused);

		assertThat(prompt).contains("Weak transitions: bn, m,");
		assertThat(prompt).contains("Least steady reach: Right ring (ol.)");
	}

	@Test
	void promptStaysQuietWithoutAnalysis() {
		String prompt = OpenAiFeedbackGenerator.userPrompt(BASE);

		assertThat(prompt).doesNotContain("Weak transitions", "Least steady reach");
	}
}
