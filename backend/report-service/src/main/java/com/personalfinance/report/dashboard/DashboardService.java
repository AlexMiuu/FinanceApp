package com.personalfinance.report.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.report.domain.ExpenseProjectionEntity;
import com.personalfinance.report.domain.ExpenseProjectionRepository;

@Service
public class DashboardService {

    public record CategorySlice(String category, long amount) {
    }

    public record DayPoint(LocalDate date, long amount) {
    }

    public record Dashboard(
            String month,
            long totalSpent,
            long mandatorySpent,
            int expenseCount,
            long previousMonthTotal,
            Long projectedMonthEnd,   // null unless the requested month is the current one
            List<CategorySlice> byCategory,
            List<DayPoint> byDay) {
    }

    private final ExpenseProjectionRepository projections;

    public DashboardService(ExpenseProjectionRepository projections) {
        this.projections = projections;
    }

    @Transactional(readOnly = true)
    public Dashboard build(UUID userId, YearMonth month, LocalDate today) {
        List<ExpenseProjectionEntity> rows = projections.findByUserIdAndExpenseDateBetween(
                userId, month.atDay(1), month.atEndOfMonth());

        long total = rows.stream().mapToLong(ExpenseProjectionEntity::getAmount).sum();
        long mandatory = rows.stream()
                .filter(ExpenseProjectionEntity::isMandatory)
                .mapToLong(ExpenseProjectionEntity::getAmount).sum();

        // Pie groups by top-level category so the slice count stays readable.
        List<CategorySlice> byCategory = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> row.getCategoryPath().split(" > ")[0],
                        Collectors.summingLong(ExpenseProjectionEntity::getAmount)))
                .entrySet().stream()
                .map(entry -> new CategorySlice(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategorySlice::category))
                .toList();

        Map<LocalDate, Long> perDay = rows.stream().collect(Collectors.groupingBy(
                ExpenseProjectionEntity::getExpenseDate,
                TreeMap::new,
                Collectors.summingLong(ExpenseProjectionEntity::getAmount)));
        List<DayPoint> byDay = month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1))
                .map(date -> new DayPoint(date, perDay.getOrDefault(date, 0L)))
                .toList();

        YearMonth previous = month.minusMonths(1);
        long previousTotal = projections.sumForRange(userId, previous.atDay(1), previous.atEndOfMonth());

        // FR-6 projection: simple linear extrapolation of the running month.
        Long projected = null;
        if (YearMonth.from(today).equals(month) && today.getDayOfMonth() > 0) {
            projected = Math.round((double) total / today.getDayOfMonth() * month.lengthOfMonth());
        }

        return new Dashboard(month.toString(), total, mandatory, rows.size(), previousTotal,
                projected, byCategory, byDay);
    }
}
