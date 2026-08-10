package com.personalfinance.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The create response, and the only place the signing secret is ever returned.
 * Every later read goes through {@link WebhookDto}, which omits it.
 */
public record WebhookCreatedDto(
        UUID id,
        String url,
        String eventPattern,
        Instant createdAt,
        String secret) {
}
