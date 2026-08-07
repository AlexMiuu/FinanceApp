package com.personalfinance.quest.dto;

import java.time.LocalDate;
import java.util.UUID;

public record GoalDto(UUID id, String name, UUID categoryId, long targetAmount, String period,
        LocalDate startDate, LocalDate endDate, boolean active,
        long currentActual, boolean currentMet, LocalDate periodStart, LocalDate periodEnd) {
}
