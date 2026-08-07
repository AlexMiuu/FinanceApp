package com.personalfinance.quest.dto;

import java.util.List;

public record CalendarDto(String month, List<CalendarDayDto> days,
        List<PeriodSummaryDto> monthlyGoals, List<PeriodSummaryDto> yearlyGoals) {
}
