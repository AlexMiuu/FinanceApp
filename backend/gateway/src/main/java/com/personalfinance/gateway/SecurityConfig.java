package com.personalfinance.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Central authentication enforcement (DESIGN.md section 4): every route except
 * the auth endpoints requires a valid access token, verified against the
 * user-service JWKS. Downstream services receive the original Authorization
 * header and can re-verify claims themselves.
 *
 * Two chains (M14/D6): {@code /api/v1/public/**} is evaluated first and
 * accepts a personal access token (opaque, introspected against user-service);
 * everything else falls through to the original JWT-only chain. Exactly one
 * chain handles a given request — Spring Security WebFlux picks the first
 * whose {@code securityMatcher} matches, in {@code @Order}.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${auth.jwks-uri:http://localhost:8081/api/v1/auth/jwks}") String jwksUri) {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri).build();
    }

    /**
     * {@code /api/v1/public/**} is GET-only by construction here — no route in
     * {@link RouteConfig} exposes a write verb under that prefix — which is
     * what makes "a read-scoped token cannot write" a structural property
     * rather than a runtime scope check that a bug could bypass. The
     * {@code hasAuthority("SCOPE_read")} check is a second, redundant layer on
     * top of that, not the only one.
     */
    @Bean
    @Order(1)
    SecurityWebFilterChain publicApiSecurityWebFilterChain(ServerHttpSecurity http,
            ReactiveOpaqueTokenIntrospector introspector) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/public/**"))
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchange -> exchange
                        .matchers(ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/api/v1/public/**"))
                        .hasAuthority("SCOPE_read")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.opaqueToken(opaque -> opaque.introspector(introspector)))
                .build();
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain jwtSecurityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/auth/**", "/actuator/**").permitAll()
                        // WS handshake can't carry headers; STOMP CONNECT is
                        // authenticated inside notification-service instead.
                        .pathMatchers("/ws/**", "/ws").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .build();
    }
}
