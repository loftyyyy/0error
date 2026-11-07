package com.rho.backend.identity;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

public class BearerTokenConverter implements ServerAuthenticationConverter {

	private static final String PREFIX = "Bearer ";

	@Override
	public Mono<Authentication> convert(ServerWebExchange exchange) {
		String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
			return Mono.empty();
		}

		String token = header.substring(PREFIX.length()).trim();
		if (token.isEmpty()) {
			return Mono.empty();
		}
		return Mono.just(new UsernamePasswordAuthenticationToken("guest", token));
	}
}
