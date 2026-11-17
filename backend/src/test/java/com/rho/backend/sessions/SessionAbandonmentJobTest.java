package com.rho.backend.sessions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAbandonmentJobTest {

	@Mock
	private SessionAbandonment abandonment;

	@Test
	void sweepsSessionsUntouchedForADay() {
		when(abandonment.abandonStale(any())).thenReturn(Mono.just(2L));

		new SessionAbandonmentJob(abandonment).abandonStale();

		verify(abandonment, timeout(1_000)).abandonStale(SessionAbandonment.STALE_AFTER);
	}
}
