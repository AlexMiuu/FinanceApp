package com.personalfinance.report.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.report.dto.CategorySliceDto;
import com.personalfinance.report.dto.DashboardDto;
import com.personalfinance.report.dto.DayPointDto;
import com.personalfinance.report.entity.ExpenseProjectionEntity;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.service.GhostFlockCalculator.GhostSeries;

@Service
public class DashboardService {

    private final ExpenseProjectionRepository projections;

    public DashboardService(ExpenseProjectionRepository projections) {
        this.projections = projections;
    }

    @Transactional(readOnly = true)
    public DashboardDto build(UUID userId, YearMonth month, LocalDate today) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(today, "today");

        List<ExpenseProjectionEntity> rows = projections.findByUserIdAndExpenseDateBetween(
                userId, month.atDay(1), month.atEndOfMonth());

        long total = rows.stream().mapToLong(ExpenseProjectionEntity::getAmount).sum();
        long mandatory = rows.stream()
                .filter(ExpenseProjectionEntity::isMandatory)
                .mapToLong(ExpenseProjectionEntity::getAmount).sum();

        // Pie groups by top-level category so the slice count stays readable.
        List<CategorySliceDto> byCategory = rows.stream()
                .collect(Collectors.groupingBy(
                        row -> GhostFlockCalculator.topLevelCategory(row.getCategoryPath()),
                        Collectors.summingLong(ExpenseProjectionEntity::getAmount)))
                .entrySet().stream()
                .map(entry -> new CategorySliceDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategorySliceDto::category))
                .toList();

        Map<LocalDate, Long> perDay = rows.stream().collect(Collectors.groupingBy(
                ExpenseProjectionEntity::getExpenseDate,
                TreeMap::new,
                Collectors.summingLong(ExpenseProjectionEntity::getAmount)));
        List<DayPointDto> byDay = month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1))
                .map(date -> new DayPointDto(date, perDay.getOrDefault(date, 0L)))
                .toList();

        YearMonth previous = month.minusMonths(1);
        long previousTotal = projections.sumForRange(userId, previous.atDay(1), previous.atEndOfMonth());

        // FR-6 projection: simple linear extrapolation of the running month.
        Long projected = null;
        if (YearMonth.from(today).equals(month) && today.getDayOfMonth() > 0) {
            projected = Math.round((double) total / today.getDayOfMonth() * month.lengthOfMonth());
        }

        Optional<GhostSeries> ghost = ghostFor(userId, month, rows);

        return new DashboardDto(month.toString(), total, mandatory, rows.size(), previousTotal,
                projected, byCategory, byDay,
                ghost.map(GhostSeries::byDay).orElse(null),
                ghost.map(GhostSeries::monthTotal).orElse(null));
    }

    /**
     * One extra indexed range read over the same projection the real figures come from —
     * the whole cost of F3 (D3), with no second stored projection to fall out of step.
     */
    private Optional<GhostSeries> ghostFor(UUID userId, YearMonth month,
            List<ExpenseProjectionEntity> monthRows) {
        List<ExpenseProjectionEntity> trailingRows = projections.findByUserIdAndExpenseDateBetween(
                userId,
                month.minusMonths(GhostFlockCalculator.TRAILING_MONTHS).atDay(1),
                month.minusMonths(1).atEndOfMonth());
        return GhostFlockCalculator.compute(month, trailingRows, monthRows);
    }
}
