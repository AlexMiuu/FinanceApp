package com.personalfinance.quest.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarDayDto(LocalDate date, DayStatusDto status, long totalSpent,
        List<GoalOutcomeDto> goals) {
}
