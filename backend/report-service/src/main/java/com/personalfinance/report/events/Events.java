package com.personalfinance.report.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published to the pf.events exchange (DESIGN.md event catalog).
 */
public final class Events {

    private Events() {
    }

    public sealed interface DomainEvent permits ErasureCompleted, WeatherUpdated {
        String routingKey();
    }

    public record ErasureCompleted(UUID erasureRequestId, UUID userId, String service, Instant occurredAt)
            implements DomainEvent {

        @Override
        public String routingKey() {
            return "user.erasure.completed";
        }
    }

    /** F4: a committed band transition, published once a candidate has dwelt out its window. */
    public record WeatherUpdated(UUID userId, String band, Instant occurredAt) implements DomainEvent {

        @Override
        public String routingKey() {
            return "ambient.weather.updated";
        }
    }

    /** Mirror of user-service's user.erasure.requested payload (consumed here). */
    public record UserErasureRequested(UUID erasureRequestId, UUID userId, Instant occurredAt) {
    }
}
