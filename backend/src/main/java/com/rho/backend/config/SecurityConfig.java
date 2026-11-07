package com.rho.backend.config;

import java.time.Duration;
import java.util.List;

import com.rho.backend.identity.BearerTokenConverter;
import com.rho.backend.identity.GuestTokenAuthenticationManager;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

	@Bean
	SecurityWebFilterChain securityWebFilterChain(
			ServerHttpSecurity http,
			GuestTokenAuthenticationManager guestAuthenticationManager) {
		AuthenticationWebFilter guestFilter = new AuthenticationWebFilter(guestAuthenticationManager);
		guestFilter.setServerAuthenticationConverter(new BearerTokenConverter());
		guestFilter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(
				new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)));

		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				.cors(Customizer.withDefaults())
				.addFilterAt(guestFilter, SecurityWebFiltersOrder.AUTHENTICATION)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.pathMatchers(HttpMethod.POST, "/api/v1/auth/guest").permitAll()
						.pathMatchers("/api/v1/**").authenticated()
						.anyExchange().denyAll())
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
				"Content-Type", "Authorization", "X-Guest-Token", "X-Guest-Id", "Last-Event-ID", "X-Request-Id"));
		configuration.setExposedHeaders(List.of("Last-Event-ID", "X-Request-Id"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
