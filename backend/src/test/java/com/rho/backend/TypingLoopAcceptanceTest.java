package com.rho.backend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.rho.backend.analysis.FingerReport;
import com.rho.backend.analysis.WeakPairReport;
import com.rho.backend.drills.DrillResponse;
import com.rho.backend.identity.GuestTokenResponse;
import com.rho.backend.progress.ProgressSummary;
import com.rho.backend.sessions.CreateSessionRequest;
import com.rho.backend.sessions.SessionCompletionResponse;
import com.rho.backend.sessions.SessionHistoryResponse;
import com.rho.backend.sessions.SessionResponse;
import com.rho.backend.telemetry.RawKeystroke;
import com.rho.backend.telemetry.TelemetryBatchRequest;
import com.rho.backend.telemetry.TelemetryIngestResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the whole product through HTTP the way the browser does: guest
 * identity, a served drill, raw telemetry, server-side scoring, the tier gate,
 * pair and reach analysis, and the coaching stream. The unit suites cover each
 * piece; this proves they compose against real security filters and Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class TypingLoopAcceptanceTest {

	private static final int PAUSE_DISPLAY_MS = 1_000;

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@Autowired
	private DatabaseClient databaseClient;

	@BeforeEach
	void bindClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@Test
	void aGuestTypesTwoCleanDrillsAndUnlocksTheNextTier() {
		GuestTokenResponse guest = client.post()
				.uri("/api/v1/auth/guest")
				.exchange()
				.expectStatus().isOk()
				.expectBody(GuestTokenResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(guest).isNotNull();
		String token = guest.token();
		UUID userId = guest.guestId();

		try {
			// Postgres is the only dependency the app uses, so the probe only has
			// to agree with a running database.
			client.get()
					.uri("/actuator/health")
					.exchange()
					.expectStatus().isOk()
					.expectBody()
					.jsonPath("$.status").isEqualTo("UP");

			ProgressSummary start = progress(token);
			assertThat(start.tiers().get(0).unlocked()).isTrue();
			assertThat(start.tiers().get(1).unlocked()).isFalse();

			// A session cannot be opened from a drill the server never issued.
			client.post()
					.uri("/api/v1/sessions")
					.headers(headers -> headers.setBearerAuth(token))
					.bodyValue(new CreateSessionRequest(
							UUID.randomUUID(), List.of(UUID.randomUUID())))
					.exchange()
					.expectStatus().isBadRequest();

			List<DrillResponse> drills = drillSet(token, 0, null);
			DrillResponse drill = drills.get(0);
			assertThat(drill.tier()).isEqualTo(0);
			assertThat(drill.source()).isEqualTo("STATIC");
			assertThat(drill.text()).hasSize(drill.length());
			assertThat(drill.text().length()).isBetween(40, 60);

			String typed = composedText(drills);
			SessionResponse session = createSession(token, drills);
			assertThat(session.status()).isEqualTo("ACTIVE");
			// The server derived the target from the issued drills, not the client.
			assertThat(session.targetLength()).isEqualTo(typed.length());
			assertThat(session.targetCharset()).isEqualTo(drill.charset());

			// The typist stumbles on every occurrence of one repeated transition
			// on the first pass; corrections never erase it.
			String pair = leastFrequentPair(drill.text());
			List<Integer> stumbles = occurrences(drill.text(), pair);
			String wrong = aDifferentAllowedKey(drill.charset(), pair.charAt(1));
			List<RawKeystroke> events = keystrokes(typed, stumbles, wrong);

			TelemetryIngestResponse ingested = sendTelemetry(token, session.id(), events);
			assertThat(ingested.received()).isEqualTo(events.size());
			assertThat(ingested.inserted()).isEqualTo(events.size());
			assertThat(ingested.duplicates()).isZero();

			SessionCompletionResponse first = complete(token, session.id());
			assertThat(first.passed()).isTrue();
			assertThat(first.failures()).isEmpty();
			assertThat(first.consecutivePasses()).isEqualTo(1);
			assertThat(first.unlockedTier()).isNull();
			assertThat(first.metrics().keystrokeCount()).isEqualTo(typed.length());
			assertThat(first.metrics().correctCount())
					.isEqualTo(typed.length() - stumbles.size());
			assertThat(first.metrics().medianInterKeyMs()).isEqualTo(300);

			// The finished drill is now the guest's most recent one, with the
			// gate decision stored alongside its metrics.
			SessionHistoryResponse history = recentSessions(token, 5);
			assertThat(history.sessions()).singleElement().satisfies(entry -> {
				assertThat(entry.sessionId()).isEqualTo(session.id());
				assertThat(entry.tier()).isZero();
				assertThat(entry.tierName()).isEqualTo("Home row");
				assertThat(entry.passed()).isTrue();
				assertThat(entry.accuracy()).isNotNull();
			});

			// The stumble survives scoring as one weak pair on a repeated transition.
			WeakPairReport pairs = weakPairs(token, 0);
			assertThat(pairs.tier()).isEqualTo(0);
			assertThat(pairs.pairs())
					.filteredOn(entry -> entry.pair().equals(pair))
					.singleElement()
					.satisfies(entry -> {
						assertThat(entry.attempts()).isEqualTo(occurrences(typed, pair).size());
						assertThat(entry.errors()).isEqualTo(stumbles.size());
						assertThat(entry.pauses()).isEqualTo(stumbles.size());
						assertThat(entry.score()).isGreaterThan(0.1);
					});

			FingerReport fingers = fingers(token, 0);
			assertThat(fingers.tier()).isEqualTo(0);
			assertThat(fingers.fingers()).isNotEmpty();
			assertThat(fingers.fingers().stream().mapToInt(FingerReport.Entry::attempts).sum())
					.isEqualTo(typed.length());
			assertThat(fingers.fingers().stream().mapToInt(FingerReport.Entry::errors).sum())
					.isEqualTo(stumbles.size());
			assertThat(fingers.fingers().stream().map(FingerReport.Entry::score).toList())
					.isSortedAccordingTo(Comparator.reverseOrder());

			// Coaching always produces text, with no AI key configured.
			List<ServerSentEvent<String>> coaching = feedback(token, session.id());
			assertThat(coaching).isNotEmpty();
			assertThat(coaching.get(coaching.size() - 1).event()).isEqualTo("done");
			assertThat(coaching.get(coaching.size() - 1).data()).isEqualTo("[DONE]");
			String advice = coaching.stream()
					.filter(event -> "token".equals(event.event()))
					.map(ServerSentEvent::data)
					.reduce("", (left, right) -> left + right);
			assertThat(advice).isNotBlank();

			// A retried completion replays the stored answer and changes nothing.
			SessionCompletionResponse replay = complete(token, session.id());
			assertThat(replay).isEqualTo(first);
			assertThat(weakPairs(token, 0).pairs()).isEqualTo(pairs.pairs());

			// A drill id is single-use: a second session cannot cite the same set.
			client.post()
					.uri("/api/v1/sessions")
					.headers(headers -> headers.setBearerAuth(token))
					.bodyValue(new CreateSessionRequest(UUID.randomUUID(), drillIds(drills)))
					.exchange()
					.expectStatus().isBadRequest();

			// The weak pair now steers the next set of drills.
			List<DrillResponse> focusedDrills = drillSet(token, 0, drill.text());
			assertThat(focusedDrills)
					.anySatisfy(focused -> assertThat(focused.text()).contains(pair));
			assertThat(focusedDrills)
					.allSatisfy(focused -> assertThat(focused.text().length()).isBetween(40, 60));

			// A second consecutive pass unlocks the next tier.
			SessionResponse secondSession = createSession(token, focusedDrills);
			String secondTyped = composedText(focusedDrills);
			sendTelemetry(token, secondSession.id(), keystrokes(secondTyped, List.of(), null));

			SessionCompletionResponse second = complete(token, secondSession.id());
			assertThat(second.passed()).isTrue();
			assertThat(second.consecutivePasses()).isEqualTo(2);
			assertThat(second.unlockedTier()).isEqualTo(1);

			ProgressSummary end = progress(token);
			assertThat(end.tiers())
					.filteredOn(status -> status.tier() == 1)
					.singleElement()
					.satisfies(status -> assertThat(status.unlocked()).isTrue());

			DrillResponse topRow = nextDrill(token, 1, null);
			assertThat(topRow.tier()).isEqualTo(1);
			assertThat(topRow.text().length()).isBetween(40, 60);
		}
		finally {
			databaseClient.sql("DELETE FROM users WHERE id = :id")
					.bind("id", userId)
					.fetch()
					.rowsUpdated()
					.block();
		}
	}

	private ProgressSummary progress(String token) {
		ProgressSummary summary = client.get()
				.uri("/api/v1/progress")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody(ProgressSummary.class)
				.returnResult()
				.getResponseBody();
		assertThat(summary).isNotNull();
		return summary;
	}

	private DrillResponse nextDrill(String token, int tier, String exclude) {
		String uri = exclude == null
				? "/api/v1/drills/next?tier=" + tier
				: "/api/v1/drills/next?tier=" + tier + "&exclude={exclude}";
		WebTestClient.RequestHeadersSpec<?> request = client.get()
				.uri(uri, exclude == null ? new Object[0] : new Object[] { exclude })
				.headers(headers -> headers.setBearerAuth(token));
		DrillResponse drill = request
				.exchange()
				.expectStatus().isOk()
				.expectBody(DrillResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(drill).isNotNull();
		return drill;
	}

	private List<DrillResponse> drillSet(String token, int tier, String exclude) {
		List<DrillResponse> drills = new ArrayList<>();
		String previous = exclude;
		for (int index = 0; index < 3; index++) {
			DrillResponse next = nextDrill(token, tier, previous);
			drills.add(next);
			previous = next.text();
		}
		return drills;
	}

	private static List<UUID> drillIds(List<DrillResponse> drills) {
		return drills.stream().map(DrillResponse::drillId).toList();
	}

	private static String composedText(List<DrillResponse> drills) {
		return drills.stream().map(DrillResponse::text).collect(Collectors.joining(" "));
	}

	private SessionResponse createSession(String token, List<DrillResponse> drills) {
		SessionResponse session = client.post()
				.uri("/api/v1/sessions")
				.headers(headers -> headers.setBearerAuth(token))
				.bodyValue(new CreateSessionRequest(UUID.randomUUID(), drillIds(drills)))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(SessionResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(session).isNotNull();
		return session;
	}

	private TelemetryIngestResponse sendTelemetry(
			String token, UUID sessionId, List<RawKeystroke> events) {
		TelemetryIngestResponse ingested = client.post()
				.uri("/api/v1/sessions/{id}/telemetry", sessionId)
				.headers(headers -> headers.setBearerAuth(token))
				.bodyValue(new TelemetryBatchRequest(
						UUID.randomUUID().toString(), sessionId, Instant.now(), events))
				.exchange()
				.expectStatus().isOk()
				.expectBody(TelemetryIngestResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(ingested).isNotNull();
		return ingested;
	}

	private SessionCompletionResponse complete(String token, UUID sessionId) {
		SessionCompletionResponse completion = client.post()
				.uri("/api/v1/sessions/{id}/complete", sessionId)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody(SessionCompletionResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(completion).isNotNull();
		return completion;
	}

	private SessionHistoryResponse recentSessions(String token, int limit) {
		SessionHistoryResponse history = client.get()
				.uri("/api/v1/sessions/recent?limit=" + limit)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody(SessionHistoryResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(history).isNotNull();
		return history;
	}

	private WeakPairReport weakPairs(String token, int tier) {
		WeakPairReport report = client.get()
				.uri("/api/v1/progress/weak-pairs?tier=" + tier)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody(WeakPairReport.class)
				.returnResult()
				.getResponseBody();
		assertThat(report).isNotNull();
		return report;
	}

	private FingerReport fingers(String token, int tier) {
		FingerReport report = client.get()
				.uri("/api/v1/progress/fingers?tier=" + tier)
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody(FingerReport.class)
				.returnResult()
				.getResponseBody();
		assertThat(report).isNotNull();
		return report;
	}

	private List<ServerSentEvent<String>> feedback(String token, UUID sessionId) {
		List<ServerSentEvent<String>> events = client.get()
				.uri("/api/v1/sessions/{id}/feedback", sessionId)
				.headers(headers -> headers.setBearerAuth(token))
				.accept(MediaType.TEXT_EVENT_STREAM)
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
				.returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {
				})
				.getResponseBody()
				.collectList()
				.block();
		assertThat(events).isNotNull();
		return events;
	}

	/** Every key is pressed 300 ms apart; stumble positions are slow and wrong. */
	private static List<RawKeystroke> keystrokes(
			String typed, List<Integer> wrongPositions, String actual) {
		Set<Integer> wrong = new HashSet<>(wrongPositions);
		List<RawKeystroke> events = new ArrayList<>(typed.length());
		for (int index = 0; index < typed.length(); index++) {
			String expected = String.valueOf(typed.charAt(index));
			boolean correct = !wrong.contains(index);
			events.add(new RawKeystroke(
					index,
					index,
					RawKeystroke.KEY,
					expected,
					correct ? expected : actual,
					correct,
					index * 300,
					correct ? 300 : PAUSE_DISPLAY_MS,
					false));
		}
		return events;
	}

	/** The least repeated bigram, so one pass seeds a clear signal. */
	private static String leastFrequentPair(String text) {
		Map<String, Integer> counts = new TreeMap<>();
		for (int index = 1; index < text.length(); index++) {
			counts.merge(text.substring(index - 1, index + 1), 1, Integer::sum);
		}
		return counts.entrySet().stream()
				.min(Map.Entry.<String, Integer>comparingByValue()
						.thenComparing(Map.Entry.comparingByKey()))
				.orElseThrow()
				.getKey();
	}

	/** Positions of the pair's second character, which is what the analyzer counts. */
	private static List<Integer> occurrences(String text, String pair) {
		List<Integer> positions = new ArrayList<>();
		for (int index = 1; index + pair.length() <= text.length(); index++) {
			if (text.startsWith(pair, index - 1)) {
				positions.add(index);
			}
		}
		return positions;
	}

	private static String aDifferentAllowedKey(String charset, char expected) {
		for (char candidate : charset.toCharArray()) {
			if (candidate != expected && candidate != ' ') {
				return String.valueOf(candidate);
			}
		}
		throw new IllegalStateException("Tier charset has only one letter");
	}
}
