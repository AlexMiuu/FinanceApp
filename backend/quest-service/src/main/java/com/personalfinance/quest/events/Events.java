package com.personalfinance.quest.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Domain events published to the pf.events exchange (DESIGN.md event catalog).
 */
public final class Events {

    private Events() {
    }

    public sealed interface DomainEvent permits ErasureCompleted, OathResolved, QuestChanged {
        String routingKey();
    }

    public record ErasureCompleted(UUID erasureRequestId, UUID userId, String service, Instant occurredAt)
            implements DomainEvent {

        @Override
        public String routingKey() {
            return "user.erasure.completed";
        }
    }

    /**
     * quest.suggested / quest.completed / quest.failed — the payload
     * notification-service's QuestEventListener consumes. Field names are the
     * wire contract; the routing key is derived from the quest's new status.
     */
    public record QuestChanged(UUID questId, UUID userId, String title, String status, Instant occurredAt)
            implements DomainEvent {

        @Override
        public String routingKey() {
            return "quest." + status.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * oath.kept / oath.slipped / oath.forgone — mirrors {@link QuestChanged}'s
     * shape so notification-service renders both from the routing key. Only
     * terminal statuses are published; an open oath is not news.
     */
    public record OathResolved(UUID oathId, UUID userId, String title, String status, Instant occurredAt)
            implements DomainEvent {

        @Override
        public String routingKey() {
            return "oath." + status.toLowerCase(Locale.ROOT);
        }
    }

    /** Mirror of user-service's user.erasure.requested payload (consumed here). */
    public record UserErasureRequested(UUID erasureRequestId, UUID userId, Instant occurredAt) {
    }

    /**
     * Loose mirror of report-service's macro.* payloads (consumed here). One
     * record covers both routing keys because a single queue bound to macro.*
     * can only deserialize into one type; unknown JSON fields are ignored and
     * fields belonging to the other event simply arrive null.
     */
    public record MacroEvent(String season, String source, LocalDate asOfDate, Instant occurredAt) {
    }
}
