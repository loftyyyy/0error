package com.rho.backend.drills;

import com.rho.backend.ai.AiChatClient;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * OpenAI-backed drill generation. It is only ever called from the background
 * prefetcher, never from a request path. A missing key or model simply yields
 * an empty result, leaving the static pool in charge.
 */
@Component
public class OpenAiDrillGenerator implements DrillGenerator {

	private static final String SYSTEM_PROMPT = """
			You write short touch-typing drills for absolute beginners.
			Reply with the drill text only. No title, no quotes, no explanation.
			""";

	private final AiChatClient chat;
	private final AiDrillProperties properties;

	public OpenAiDrillGenerator(AiChatClient chat, AiDrillProperties properties) {
		this.chat = chat;
		this.properties = properties;
	}

	@Override
	public Mono<String> generate(TierDefinition tier, String exclude) {
		if (!properties.enabled()) {
			return Mono.empty();
		}

		return Mono.fromCallable(() -> chat.maybeGet()
					.map(client -> clean(client.prompt()
							.system(SYSTEM_PROMPT)
							.user(userPrompt(tier, exclude))
							.call()
							.content()))
					.orElse(null))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private static String userPrompt(TierDefinition tier, String exclude) {
		StringBuilder prompt = new StringBuilder()
				.append("Write one drill between ").append(tier.minLength()).append(" and ")
				.append(tier.maxLength()).append(" characters long.\n")
				.append("Use only characters from this list, including the space: ")
				.append(tier.charset()).append('\n')
				.append("Keep it rhythmic and easy to type. Never emit a newline.\n");

		if (exclude != null && !exclude.isBlank()) {
			prompt.append("Do not repeat this drill: ").append(exclude).append('\n');
		}
		return prompt.toString();
	}

	/** Removes wrappers and line breaks a model sometimes adds around the text. */
	static String clean(String raw) {
		if (raw == null) {
			return null;
		}

		String text = raw.strip();
		if (text.startsWith("```")) {
			text = text.substring(3);
			int closing = text.lastIndexOf("```");
			if (closing >= 0) {
				text = text.substring(0, closing);
			}
			text = text.strip();

			// Drop an optional fence language label on its own first line.
			int newline = text.indexOf('\n');
			if (newline >= 0) {
				String label = text.substring(0, newline).strip();
				if (label.matches("[A-Za-z0-9_+-]{0,20}")) {
					text = text.substring(newline + 1);
				}
			}
		}

		text = text.strip();
		if (text.length() >= 2) {
			char first = text.charAt(0);
			char last = text.charAt(text.length() - 1);
			if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
					|| (first == '`' && last == '`')) {
				text = text.substring(1, text.length() - 1).strip();
			}
		}

		text = text.replaceAll("\\s+", " ").strip();
		return text.isEmpty() ? null : text;
	}
}
