package com.personalfinance.gateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.ReactiveOpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Resolves a personal access token by calling user-service's introspection
 * endpoint (M14/D6), which returns a freshly minted short-lived JWT rather
 * than raw claims. That JWT is decoded here through the *same*
 * {@link ReactiveJwtDecoder} the normal chain uses — it is signed by the same
 * key, so this is a real signature/expiry check, not a bare parse — and the
 * decoded token is stashed on the principal so
 * {@link TokenExchangeForwardingFilter} can swap it into the outbound request
 * before the gateway forwards downstream. This is what lets
 * expense/report/quest services stay completely unaware that a personal
 * access token was ever involved.
 */
@Component
public class PersonalAccessTokenIntrospector implements ReactiveOpaqueTokenIntrospector {

    private final WebClient webClient;
    private final ReactiveJwtDecoder jwtDecoder;

    public PersonalAccessTokenIntrospector(
            @Value("${services.user.url:http://localhost:8081}") String userServiceUrl,
            ReactiveJwtDecoder jwtDecoder) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Mono<OAuth2AuthenticatedPrincipal> introspect(String token) {
        return webClient.post()
                .uri("/api/v1/auth/introspect")
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(IntrospectionResponse.class)
                .onErrorMap(e -> !(e instanceof BadOpaqueTokenException),
                        e -> new BadOpaqueTokenException("Introspection request failed", e))
                .flatMap(response -> {
                    if (!response.active() || response.accessToken() == null || response.accessToken().isBlank()) {
                        return Mono.error(new BadOpaqueTokenException("Token is not active"));
                    }
                    return jwtDecoder.decode(response.accessToken())
                            .map(jwt -> toPrincipal(jwt, response.accessToken()))
                            .onErrorMap(e -> !(e instanceof BadOpaqueTokenException),
                                    e -> new BadOpaqueTokenException("Exchanged token failed validation", e));
                });
    }

    private OAuth2AuthenticatedPrincipal toPrincipal(Jwt jwt, String exchangedToken) {
        String scope = jwt.getClaimAsString("scope");
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("SCOPE_" + scope));
        Map<String, Object> attributes = new HashMap<>(jwt.getClaims());
        attributes.put(TokenExchangeForwardingFilter.EXCHANGED_TOKEN_ATTRIBUTE, exchangedToken);
        return new DefaultOAuth2AuthenticatedPrincipal(jwt.getSubject(), attributes, authorities);
    }

    private record IntrospectionResponse(boolean active, String accessToken, String scope) {
    }
}
