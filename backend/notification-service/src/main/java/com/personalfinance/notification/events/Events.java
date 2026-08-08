package com.personalfinance.notification.events;

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

    /** Loose mirror of quest-service's quest.* payload; unknown JSON fields are ignored. */
    public record QuestEvent(UUID questId, UUID userId, String title, String status, Instant occurredAt) {
    }

    /** Mirror of report-service's ambient.weather.updated payload (consumed here). */
    public record WeatherEvent(UUID userId, String band, Instant occurredAt) {
    }
}
