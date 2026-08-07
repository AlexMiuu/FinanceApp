package com.personalfinance.quest.dto;

import java.util.UUID;

public record PeriodSummaryDto(UUID goalId, String name, long target, long actual, boolean met,
        boolean inProgress) {
}
