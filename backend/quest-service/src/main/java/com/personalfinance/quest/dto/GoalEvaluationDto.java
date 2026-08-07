package com.personalfinance.quest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalEvaluationDto(UUID id, UUID goalId, LocalDate periodStart, LocalDate periodEnd,
        long actualAmount, boolean met, Instant evaluatedAt) {
}
