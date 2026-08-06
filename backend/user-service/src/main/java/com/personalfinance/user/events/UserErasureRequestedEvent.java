package com.personalfinance.user.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Fanout signal that a user asked for erasure. Every service holding that
 * user's data deletes its copy and acks with "user.erasure.completed" carrying
 * the same erasureRequestId.
 */
public record UserErasureRequestedEvent(UUID erasureRequestId, UUID userId, Instant occurredAt) {
}
