package com.personalfinance.gateway;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Throttles routes that accept unauthenticated callers, keyed by client address.
 *
 * {@link PublicApiRateLimiterFilter} cannot be reused here: it keys on the
 * authenticated principal, so every anonymous caller would collapse into one
 * shared bucket and the first visitor to spend it would lock out everyone else.
 */
@Component
public class AnonymousRateLimiterFilter implements GatewayFilter {

    /** Burst allowance — a visitor trying a handful of figures in a row is normal. */
    private static final double CAPACITY = 20;
    /** Sustained rate: 20 requests/minute. */
    private static final double REFILL_PER_SECOND = 1.0 / 3.0;

    private final TokenBucketRegistry buckets = new TokenBucketRegistry(CAPACITY, REFILL_PER_SECOND);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (buckets.tryConsume(clientKey(exchange))) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    /**
     * The caller's address, preferring the left-most {@code X-Forwarded-For} entry:
     * the bundled nginx proxies {@code /api}, so the socket address is nginx itself
     * and keying on it would put every browser in one bucket.
     *
     * That header is supplied by the caller and so can be forged. This therefore
     * bounds accidental hammering — a stuck retry loop, a scraper — rather than a
     * determined attacker, which is the right trade for an endpoint that only does
     * arithmetic, reads no data and writes none.
     */
    private static String clientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }
}
