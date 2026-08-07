package com.personalfinance.quest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The stored goal row as-is. Distinct from {@link GoalDto}, which decorates a
 * goal with its current-period progress: an Art. 20 export reports what is
 * held, not what is computed at read time.
 */
public record GoalExportDto(UUID id, String name, String type, UUID categoryId, long targetAmount,
        String period, LocalDate startDate, LocalDate endDate, boolean active, Instant createdAt) {
}
