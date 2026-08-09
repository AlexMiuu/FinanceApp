package com.personalfinance.quest.dto;

import java.time.Instant;
import java.util.UUID;

/** An oath as the client sees it. {@code status} is OPEN|KEPT|SLIPPED|FORGONE. */
public record OathDto(UUID id, UUID categoryId, String categoryName, long pledgedAmount,
        Instant createdAt, Instant expiresAt, String status, Instant resolvedAt, UUID matchedExpenseId) {
}
