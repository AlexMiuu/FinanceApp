package com.personalfinance.quest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** The stored quest row as-is, including the tailoring parameters held against it. */
public record QuestExportDto(UUID id, String templateCode, String title, Map<String, Object> params,
        LocalDate periodStart, LocalDate periodEnd, String status, long progressAmount,
        Instant createdAt, Instant updatedAt) {
}
