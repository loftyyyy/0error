package com.rho.backend.identity;

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
class GuestRetentionJobTest {

	@Mock
	private GuestRetention retention;

	@Test
	void sweepsGuestsDormantForAMonth() {
		when(retention.deleteDormant(any())).thenReturn(Mono.just(3L));

		new GuestRetentionJob(retention).deleteDormant();

		verify(retention, timeout(1_000)).deleteDormant(GuestRetention.DORMANT_AFTER);
	}
}
