package com.personalfinance.quest.dto;

import java.time.Instant;
import java.util.UUID;

/** The stored oath row as-is, for the Art. 20 export. */
public record OathExportDto(UUID id, UUID categoryId, String categoryName, long pledgedAmount,
        String status, UUID matchedExpenseId, Instant expiresAt, Instant resolvedAt,
        Instant createdAt, Instant updatedAt) {
}
