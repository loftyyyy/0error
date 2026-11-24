package com.rho.backend;

import com.rho.backend.drills.DrillResponse;
import com.rho.backend.drills.DrillValidator;
import com.rho.backend.drills.TierCatalogue;
import com.rho.backend.drills.TierDefinition;
import com.rho.backend.identity.GuestTokenResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the explicit focus parameter through HTTP, including the ownership
 * boundary the browser will rely on for a "practise this pair" action.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class DrillFocusAcceptanceTest {

	@LocalServerPort
	private int port;

	private WebTestClient client;

	@BeforeEach
	void bindClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://localhost:" + port)
				.build();
	}

	@Test
	void aGuestCanAskForOnePairAndGetsADrillThatExercisesIt() {
		String token = guestToken();

		DrillResponse drill = client.get()
				.uri("/api/v1/drills/next?tier=0&focus=fj")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isOk()
				.expectBody(DrillResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(drill).isNotNull();
		assertThat(drill.tier()).isZero();
		assertThat(drill.text()).contains("fj");

		TierDefinition tier = new TierCatalogue().require(0);
		assertThat(new DrillValidator().validate(drill.text(), tier))
				.as("focused drill must still fit the tier")
				.isEmpty();
	}

	@Test
	void aPairOutsideTheTierCharsetIsRejected() {
		String token = guestToken();

		client.get()
				.uri("/api/v1/drills/next?tier=0&focus=zz")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void aMalformedFocusIsRejected() {
		String token = guestToken();

		client.get()
				.uri("/api/v1/drills/next?tier=0&focus=f")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isBadRequest();
	}

	@Test
	void aLockedTierIsStillForbiddenEvenWithFocus() {
		String token = guestToken();

		client.get()
				.uri("/api/v1/drills/next?tier=1&focus=fj")
				.headers(headers -> headers.setBearerAuth(token))
				.exchange()
				.expectStatus().isForbidden();
	}

	private String guestToken() {
		GuestTokenResponse guest = client.post()
				.uri("/api/v1/auth/guest")
				.exchange()
				.expectStatus().isOk()
				.expectBody(GuestTokenResponse.class)
				.returnResult()
				.getResponseBody();
		assertThat(guest).isNotNull();
		return guest.token();
	}
}
