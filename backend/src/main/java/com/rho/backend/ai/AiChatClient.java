package com.rho.backend.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Single place where the optional LLM client is built. Both drill generation
 * and coaching check {@link #maybeGet()} and fall back when it is empty.
 */
@Component
public class AiChatClient {

	private final ObjectProvider<ChatModel> chatModelProvider;
	private final String apiKey;

	private volatile ChatClient client;

	public AiChatClient(
			ObjectProvider<ChatModel> chatModelProvider,
			@Value("${spring.ai.openai.api-key:unset}") String apiKey) {
		this.chatModelProvider = chatModelProvider;
		this.apiKey = apiKey;
	}

	public Optional<ChatClient> maybeGet() {
		if (apiKey == null || apiKey.isBlank() || "unset".equals(apiKey)) {
			return Optional.empty();
		}

		ChatClient existing = client;
		if (existing != null) {
			return Optional.of(existing);
		}

		ChatModel model = chatModelProvider.getIfAvailable();
		if (model == null) {
			return Optional.empty();
		}

		synchronized (this) {
			if (client == null) {
				client = ChatClient.create(model);
			}
			return Optional.of(client);
		}
	}
}
