package com.rho.backend.ai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatClientTest {

	@Test
	void completePrefersThePrimaryWhenItAnswers() {
		AiChatClient client = new AiChatClient(client(model("primary")), client(model("fallback")));

		assertThat(client.complete("system", "user").block()).isEqualTo("primary");
	}

	@Test
	void completeFallsBackWhenThePrimaryFails() {
		AiChatClient client = new AiChatClient(client(failing()), client(model("fallback")));

		assertThat(client.complete("system", "user").block()).isEqualTo("fallback");
	}

	@Test
	void completeFallsBackWhenThePrimaryReturnsNothing() {
		AiChatClient client = new AiChatClient(client(model("  ")), client(model("fallback")));

		assertThat(client.complete("system", "user").block()).isEqualTo("fallback");
	}

	@Test
	void streamFallsBackWhenThePrimaryFailsBeforeItsFirstToken() {
		AiChatClient client = new AiChatClient(client(failing()), client(model("fallback")));

		StepVerifier.create(client.stream("system", "user"))
				.expectNext("fallback")
				.verifyComplete();
	}

	@Test
	void streamKeepsAPartialPrimaryAnswerInsteadOfSplicingProviders() {
		ChatModel partial = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				throw new IllegalStateException("not used");
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.concat(
						Flux.just(response("half")),
						Flux.error(new IllegalStateException("connection lost")));
			}
		};
		AiChatClient client = new AiChatClient(client(partial), client(model("fallback")));

		StepVerifier.create(client.stream("system", "user"))
				.expectNext("half")
				.verifyComplete();
	}

	@Test
	void noConfiguredProviderYieldsEmptyResults() {
		AiChatClient client = new AiChatClient(null, null);

		assertThat(client.isConfigured()).isFalse();
		assertThat(client.complete("system", "user").block()).isNull();
		StepVerifier.create(client.stream("system", "user")).verifyComplete();
	}

	private static ChatClient client(ChatModel model) {
		return ChatClient.create(model);
	}

	private static ChatModel model(String reply) {
		return new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				return response(reply);
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.just(response(reply));
			}
		};
	}

	private static ChatModel failing() {
		return new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				throw new IllegalStateException("provider down");
			}

			@Override
			public Flux<ChatResponse> stream(Prompt prompt) {
				return Flux.error(new IllegalStateException("provider down"));
			}
		};
	}

	private static ChatResponse response(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}
}
