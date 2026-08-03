package com.personalfinance.user.service;

import java.time.LocalDate;
import java.util.List;

import com.personalfinance.user.entity.IncomeSourceEntity;

/**
 * Normalizes income sources to a single monthly figure (bani). Used by both the
 * net-worth endpoint and the income.updated event, so the two can never drift.
 */
public final class IncomeCalculator {

    private IncomeCalculator() {
    }

    public static long monthlyIncome(List<IncomeSourceEntity> sources, LocalDate on) {
        if (sources == null || sources.isEmpty()) {
            return 0L;
        }
        return sources.stream()
                .filter(i -> i.getStartDate() != null && !i.getStartDate().isAfter(on))
                .filter(i -> i.getEndDate() == null || !i.getEndDate().isBefore(on))
                .mapToLong(i -> switch (i.getRecurrence() == null ? "" : i.getRecurrence()) {
                    case "MONTHLY" -> i.getAmount();
                    case "YEARLY" -> Math.round(i.getAmount() / 12.0);
                    default -> 0L;   // ONE_OFF doesn't contribute to recurring income
                })
                .sum();
    }
}
