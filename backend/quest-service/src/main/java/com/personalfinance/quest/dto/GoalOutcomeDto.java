package com.personalfinance.quest.dto;

import java.util.UUID;

public record GoalOutcomeDto(UUID goalId, String name, long target, long actual, boolean met) {
}
