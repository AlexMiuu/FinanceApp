package com.personalfinance.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * A conservative in-memory token bucket scoped to {@code /api/v1/public/**}
 * and keyed by the authenticated principal (user id). {@code docker-compose.yml}
 * provisions no Redis, and Spring Cloud Gateway's built-in
 * {@code RequestRateLimiter} is Redis-only, so this is a deliberately smaller
 * substitute rather than adding infrastructure for a single-owner app — the
 * goal is catching a runaway script against one's own API, not multi-tenant
 * fairness, so a per-process in-memory bucket is enough. It resets on
 * restart; that is an accepted tradeoff at this scale.
 */
@Component
public class PublicApiRateLimiterFilter implements GatewayFilter {

    /** Burst allowance. */
    private static final double CAPACITY = 30;
    /** Sustained rate: 30 requests/minute. */
    private static final double REFILL_PER_SECOND = 0.5;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty("anonymous")
                .flatMap(principal -> {
                    Bucket bucket = buckets.computeIfAbsent(principal, key -> new Bucket());
                    if (bucket.tryConsume()) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return exchange.getResponse().setComplete();
                });
    }

    private static final class Bucket {
        private double tokens = CAPACITY;
        private long lastRefillNanos = System.nanoTime();

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(CAPACITY, tokens + elapsedSeconds * REFILL_PER_SECOND);
            lastRefillNanos = now;
        }
    }
}
