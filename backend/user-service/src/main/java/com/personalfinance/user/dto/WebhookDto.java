package com.personalfinance.user.dto;

import java.time.Instant;
import java.util.UUID;

/** A subscription as listed back. Deliberately carries no secret. */
public record WebhookDto(
        UUID id,
        String url,
        String eventPattern,
        Instant createdAt,
        Instant disabledAt,
        int consecutiveFailures) {
}
