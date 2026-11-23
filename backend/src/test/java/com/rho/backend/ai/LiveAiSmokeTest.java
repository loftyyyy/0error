package com.rho.backend.ai;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import com.rho.backend.drills.DrillFocus;
import com.rho.backend.drills.DrillGenerator;
import com.rho.backend.drills.DrillValidator;
import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.drills.TierDefinition;
import com.rho.backend.feedback.FeedbackContext;
import com.rho.backend.feedback.FeedbackGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the configured model really answers, which the default suite cannot do
 * without a key. Off by default: run with RUN_AI_LIVE_TESTS=true and the AI_*
 * variables set. It boots the whole context, so it also needs the database the
 * other integration tests use.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_AI_LIVE_TESTS", matches = "true")
class LiveAiSmokeTest {

	@Autowired
	private DrillGenerator drills;

	@Autowired
	private FeedbackGenerator feedback;

	@Autowired
	private TierCatalogue catalogue;

	@Autowired
	private DrillValidator validator;

	@Test
	void theModelWritesADrillTheTierAccepts() {
		TierDefinition tier = catalogue.require(0);

		String text = drills.generate(tier, DrillFocus.none(), null)
				.block(Duration.ofSeconds(90));

		assertThat(text)
				.as("the model returned no text; check AI_API_KEY, AI_BASE_URL and AI_MODEL")
				.isNotBlank();
		assertThat(validator.validate(text, tier))
				.as("generated drill must fit the tier: %s", text)
				.isEmpty();
	}

	@Test
	void theModelStreamsCoaching() {
		FeedbackContext context = new FeedbackContext(
				0,
				"Home row",
				140,
				132,
				new BigDecimal("94.29"),
				420,
				810,
				2,
				List.of("dk"),
				"Right ring");

		List<String> chunks = feedback.stream(context)
				.collectList()
				.block(Duration.ofSeconds(90));

		assertThat(chunks).isNotNull();
		assertThat(String.join("", chunks))
				.as("the model streamed no coaching; check the provider accepts streaming")
				.isNotBlank();
	}
}
