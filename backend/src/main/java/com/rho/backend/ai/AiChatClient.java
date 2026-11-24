package com.rho.backend.ai;

import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The optional model client, with one fallback provider behind the primary.
 *
 * <p>Both providers are OpenAI-compatible endpoints, so a single client shape
 * serves them. The primary comes from Spring AI's auto-configuration
 * ({@code spring.ai.openai.*}). The fallback is built here from its own key,
 * base URL, and model, because Spring AI configures one OpenAI model only.
 *
 * <p>A missing key never breaks anything: callers see an empty result and use
 * the static drill pool or deterministic coaching. A configured but failing
 * provider is tried, then the other one, then the caller's fallback.
 */
@Component
public class AiChatClient {

	private static final Logger log = LoggerFactory.getLogger(AiChatClient.class);

	private static final String UNSET = "unset";

	private final ChatClient primary;
	private final ChatClient fallback;

	AiChatClient(ChatClient primary, ChatClient fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	@Autowired
	public AiChatClient(
			ObjectProvider<ChatModel> models,
			@Value("${spring.ai.openai.api-key:unset}") String primaryKey,
			@Value("${app.ai.fallback.api-key:unset}") String fallbackKey,
			@Value("${app.ai.fallback.base-url:https://integrate.api.nvidia.com/v1}") String fallbackBaseUrl,
			@Value("${app.ai.fallback.model:google/gemma-4-31b-it}") String fallbackModel,
			@Value("${app.ai.fallback.temperature:0.5}") double fallbackTemperature) {
		this(primaryClient(models, primaryKey),
				fallbackClient(fallbackKey, fallbackBaseUrl, fallbackModel, fallbackTemperature));
	}

	/** True when either provider has a key; useful for diagnostics and tests. */
	public boolean isConfigured() {
		return primary != null || fallback != null;
	}

	/**
	 * One non-streaming answer, primary first. An error or blank reply from the
	 * primary moves to the fallback instead of hiding it.
	 */
	public Mono<String> complete(String system, String user) {
		return Mono.defer(() -> {
			Mono<String> first = call(primary, "primary", system, user);
			return first.switchIfEmpty(Mono.defer(
							() -> call(fallback, "fallback", system, user)))
					.subscribeOn(Schedulers.boundedElastic());
		});
	}

	/**
	 * Streamed answer, primary first. A stream that fails before its first token
	 * moves to the fallback; a stream that fails after emitting keeps what it
	 * sent, so the client never sees the two answers spliced.
	 */
	public Flux<String> stream(String system, String user) {
		return Flux.defer(() -> {
			Flux<String> first = streamWith(primary, "primary", system, user);
			if (first == null) {
				return notNull(streamWith(fallback, "fallback", system, user));
			}

			return first.switchIfEmpty(Flux.defer(
					() -> notNull(streamWith(fallback, "fallback", system, user))));
		});
	}

	private static Mono<String> call(
			ChatClient client, String label, String system, String user) {
		if (client == null) {
			return Mono.empty();
		}
		return Mono.fromCallable(() -> client.prompt()
						.system(system)
						.user(user)
						.call()
						.content())
				.filter(text -> text != null && !text.isBlank())
				.onErrorResume(error -> {
					log.warn("{} model call failed", label, error);
					return Mono.empty();
				});
	}

	private static Flux<String> streamWith(
			ChatClient client, String label, String system, String user) {
		if (client == null) {
			return null;
		}
		return client.prompt()
				.system(system)
				.user(user)
				.stream()
				.content()
				.filter(token -> token != null && !token.isEmpty())
				.onErrorResume(error -> {
					log.warn("{} model stream failed", label, error);
					return Flux.empty();
				});
	}

	private static Flux<String> notNull(Flux<String> stream) {
		return stream == null ? Flux.empty() : stream;
	}

	private static ChatClient primaryClient(ObjectProvider<ChatModel> models, String key) {
		if (!configured(key)) {
			return null;
		}
		ChatModel model = models.getIfAvailable();
		return model == null ? null : ChatClient.create(model);
	}

	private static ChatClient fallbackClient(
			String key, String baseUrl, String model, double temperature) {
		if (!configured(key)) {
			return null;
		}
		try {
			ClientOptions options = ClientOptions.Companion.builder()
					.httpClient(SpringAiOpenAiHttpClient.builder().build())
					.apiKey(key)
					.baseUrl(baseUrl)
					.build();
			OpenAiChatModel fallbackModel = OpenAiChatModel.builder()
					.openAiClient(new OpenAIClientImpl(options))
					.openAiClientAsync(new OpenAIClientAsyncImpl(options))
					.options(OpenAiChatOptions.builder()
							.model(model)
							.temperature(temperature)
							.topP(1.0)
							.build())
					.build();
			return ChatClient.create(fallbackModel);
		}
		catch (RuntimeException error) {
			log.warn("Could not configure fallback model {}", model, error);
			return null;
		}
	}

	private static boolean configured(String key) {
		return key != null && !key.isBlank() && !UNSET.equals(key);
	}
}
