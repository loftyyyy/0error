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
 * Proves the fallback provider answers when the primary cannot. The primary is
 * pointed at a model that does not exist, so any usable output came from the
 * fallback configured through AI_FALLBACK_*. Off by default and needs network.
 */
@SpringBootTest(properties = "spring.ai.openai.chat.options.model=nvidia/not-a-real-model")
@EnabledIfEnvironmentVariable(named = "RUN_AI_FAILOVER_TESTS", matches = "true")
class LiveAiFailoverSmokeTest {

	@Autowired
	private DrillGenerator drills;

	@Autowired
	private FeedbackGenerator feedback;

	@Autowired
	private TierCatalogue catalogue;

	@Autowired
	private DrillValidator validator;

	@Test
	void theFallbackWritesAValidDrillWhenThePrimaryFails() {
		TierDefinition tier = catalogue.require(0);

		String text = drills.generate(tier, DrillFocus.none(), null)
				.block(Duration.ofSeconds(90));

		assertThat(text)
				.as("fallback returned no text; check AI_FALLBACK_API_KEY and AI_FALLBACK_MODEL")
				.isNotBlank();
		assertThat(validator.validate(text, tier))
				.as("fallback drill must fit the tier: %s", text)
				.isEmpty();
	}

	@Test
	void theFallbackStreamsCoachingWhenThePrimaryFails() {
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
				.as("fallback streamed no coaching; check the fallback model supports streaming")
				.isNotBlank();
	}
}
