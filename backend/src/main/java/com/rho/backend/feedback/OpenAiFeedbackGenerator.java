package com.rho.backend.feedback;

import java.util.Optional;

import com.rho.backend.ai.AiChatClient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Streams an LLM coaching note. The prompt is deliberately narrow: a few calm
 * sentences about the metrics, never new telemetry or medical advice.
 */
@Component
public class OpenAiFeedbackGenerator implements FeedbackGenerator {

	private static final String SYSTEM_PROMPT = """
			You are a calm, concise touch-typing coach.
			Reply in at most three short sentences of plain prose.
			Be specific and encouraging. Never use lists, headings, or technical jargon.
			""";

	private final AiChatClient chat;

	public OpenAiFeedbackGenerator(AiChatClient chat) {
		this.chat = chat;
	}

	@Override
	public Flux<String> stream(FeedbackContext context) {
		return Flux.defer(() -> {
					Optional<ChatClient> client = chat.maybeGet();
					if (client.isEmpty()) {
						return Flux.<String>empty();
					}
					return client.get()
							.prompt()
							.system(SYSTEM_PROMPT)
							.user(userPrompt(context))
							.stream()
							.content();
				})
				.subscribeOn(Schedulers.boundedElastic());
	}

	private static String userPrompt(FeedbackContext context) {
		return """
				Tier: %s
				Keystrokes: %d
				Correct first attempts: %d
				Accuracy: %s
				Median gap between keys: %s
				95th percentile gap: %s
				Long pauses: %d
				""".formatted(
				context.tierName(),
				context.keystrokeCount(),
				context.correctCount(),
				context.accuracy() == null ? "unknown" : context.accuracy() + "%",
				context.medianInterKeyMs() == null ? "unknown" : context.medianInterKeyMs() + " ms",
				context.p95InterKeyMs() == null ? "unknown" : context.p95InterKeyMs() + " ms",
				context.hesitationCount());
	}
}
