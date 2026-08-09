package com.personalfinance.report.dto;

import java.util.List;

public record DashboardDto(
        String month,
        long totalSpent,
        long mandatorySpent,
        int expenseCount,
        long previousMonthTotal,
        Long projectedMonthEnd,   // null unless the requested month is the current one
        List<CategorySliceDto> byCategory,
        List<DayPointDto> byDay,
        List<DayPointDto> ghostByDay,   // null until three trailing months carry data (F3)
        Long ghostMonthTotal) {
}
