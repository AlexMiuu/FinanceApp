package com.personalfinance.report.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardDto(
        String month,
        long totalSpent,
        long mandatorySpent,
        int expenseCount,
        long previousMonthTotal,
        Long projectedMonthEnd,   // null unless the requested month is the current one
        Long projectedMonthEndSeasonal,   // null under the same condition as projectedMonthEnd
        List<CategorySliceDto> byCategory,
        List<DayPointDto> byDay,
        List<DayPointDto> ghostByDay,   // null until three trailing months carry data (F3)
        Long ghostMonthTotal,
        String macroSeason,       // wire name of the committed pastoral season; null until macro ingestion has run
        String macroSource,       // null alongside macroSeason
        LocalDate macroAsOfDate,  // null alongside macroSeason
        boolean macroStale) {
}
