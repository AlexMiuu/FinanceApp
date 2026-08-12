package com.personalfinance.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Runs after Spring Security's authentication (Gateway filters execute inside
 * {@code DispatcherHandler}, which every {@code WebFilter} — including the
 * security chain — has already passed by the time this runs; the
 * {@code ReactiveSecurityContextHolder} context set during authentication
 * survives via the Reactor context).
 *
 * For a request authenticated through the opaque-token chain, the principal
 * carries the short-lived JWT {@link PersonalAccessTokenIntrospector} already
 * validated. This filter swaps it into the outbound Authorization header
 * before the request leaves the gateway, so the downstream service sees an
 * ordinary access token — no personal-access-token awareness required there.
 *
 * A no-op for every other route: a JWT-authenticated request's principal is a
 * plain {@code Jwt}, not an {@code OAuth2AuthenticatedPrincipal}, so the
 * {@code instanceof} check below simply falls through unchanged.
 */
@Component
public class TokenExchangeForwardingFilter implements GlobalFilter, Ordered {

    static final String EXCHANGED_TOKEN_ATTRIBUTE = "argali.internal-access-token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .flatMap(auth -> {
                    if (auth != null && auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                        String exchanged = principal.getAttribute(EXCHANGED_TOKEN_ATTRIBUTE);
                        if (exchanged != null) {
                            ServerHttpRequest mutated = exchange.getRequest().mutate()
                                    .header("Authorization", "Bearer " + exchanged)
                                    .build();
                            return chain.filter(exchange.mutate().request(mutated).build());
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
