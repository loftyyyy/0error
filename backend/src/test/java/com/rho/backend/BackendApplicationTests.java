package com.rho.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context boot needs Postgres and Redis. Run with RUN_INTEGRATION_TESTS=true
 * once docker compose is up; the default build skips it.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
