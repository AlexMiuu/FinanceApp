package com.personalfinance.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * A conservative in-memory token bucket scoped to {@code /api/v1/public/**}
 * and keyed by the authenticated principal (user id) — the goal is catching a
 * runaway script against one's own API, not multi-tenant fairness. See
 * {@link TokenBucketRegistry} for why the buckets are in-process.
 *
 * Every route this is attached to requires a personal access token, so the
 * principal is always present. Routes that accept anonymous callers must use
 * {@link AnonymousRateLimiterFilter} instead: keying those on the principal
 * would put every unauthenticated visitor into one shared bucket.
 */
@Component
public class PublicApiRateLimiterFilter implements GatewayFilter {

    /** Burst allowance. */
    private static final double CAPACITY = 30;
    /** Sustained rate: 30 requests/minute. */
    private static final double REFILL_PER_SECOND = 0.5;

    private final TokenBucketRegistry buckets = new TokenBucketRegistry(CAPACITY, REFILL_PER_SECOND);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty("anonymous")
                .flatMap(principal -> {
                    if (buckets.tryConsume(principal)) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }
}
