package com.rho.backend.identity;

import java.util.List;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class GuestTokenAuthenticationManager implements ReactiveAuthenticationManager {

	private final UserRepository users;

	public GuestTokenAuthenticationManager(UserRepository users) {
		this.users = users;
	}

	@Override
	public Mono<Authentication> authenticate(Authentication authentication) {
		Object credentials = authentication.getCredentials();
		if (!(credentials instanceof String token) || token.isBlank()) {
			return Mono.error(new BadCredentialsException("Missing guest token"));
		}

		return users.findByGuestTokenHash(GuestTokens.hash(token))
				.map(user -> {
					GuestPrincipal principal = new GuestPrincipal(user.id(), user.kind());
					return (Authentication) new UsernamePasswordAuthenticationToken(
							principal,
							token,
							List.of(new SimpleGrantedAuthority("ROLE_" + user.kind())));
				})
				.switchIfEmpty(Mono.error(new BadCredentialsException("Invalid guest token")));
	}
}
