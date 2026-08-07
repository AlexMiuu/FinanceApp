package com.personalfinance.report.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain events published to the pf.events exchange (DESIGN.md event catalog).
 */
public final class Events {

    private Events() {
    }

    public sealed interface DomainEvent permits ErasureCompleted {
        String routingKey();
    }

    public record ErasureCompleted(UUID erasureRequestId, UUID userId, String service, Instant occurredAt)
            implements DomainEvent {

        @Override
        public String routingKey() {
            return "user.erasure.completed";
        }
    }

    /** Mirror of user-service's user.erasure.requested payload (consumed here). */
    public record UserErasureRequested(UUID erasureRequestId, UUID userId, Instant occurredAt) {
    }
}
