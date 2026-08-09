package com.personalfinance.report.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.personalfinance.report.dto.DayPointDto;
import com.personalfinance.report.entity.ExpenseProjectionEntity;

/**
 * F3 Ghost Flock (roadmap D3): the counterfactual month in which non-mandatory spend is
 * held at its trailing 3-month median for the same category mix, while mandatory spend
 * stays exactly as it really happened.
 *
 * <p>Computed at read time from the same {@code expense_projection} rows the real figures
 * come from. A stored second projection would introduce the class of bug where real and
 * ghost disagree because one consumer lagged.
 */
final class GhostFlockCalculator {

    static final int TRAILING_MONTHS = 3;

    private GhostFlockCalculator() {
    }

    /** The ghost month: its full-month total and the per-day amounts that sum to it. */
    record GhostSeries(long monthTotal, List<DayPointDto> byDay) {
    }

    /**
     * Groups by the first path segment so the ghost's category mix is the same mix the
     * dashboard's {@code byCategory} slices use. The two must not drift apart.
     */
    static String topLevelCategory(String categoryPath) {
        return categoryPath.split(" > ")[0];
    }

    /**
     * Empty unless every one of the {@value #TRAILING_MONTHS} trailing months carries data.
     * A shorter history has no baseline to hold spend at, and a zero-filled ghost would read
     * as "you used to spend nothing" rather than "not enough history yet".
     */
    static Optional<GhostSeries> compute(YearMonth month,
            List<ExpenseProjectionEntity> trailingRows,
            List<ExpenseProjectionEntity> monthRows) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(trailingRows, "trailingRows");
        Objects.requireNonNull(monthRows, "monthRows");

        List<YearMonth> trailing = trailingMonths(month);
        if (!monthsCarryingData(trailingRows).containsAll(trailing)) {
            return Optional.empty();
        }

        long baseline = baselineNonMandatorySpend(trailing, trailingRows);
        Map<LocalDate, Long> mandatoryPerDay = mandatoryPerDay(monthRows);
        long mandatoryTotal = mandatoryPerDay.values().stream().mapToLong(Long::longValue).sum();

        return Optional.of(new GhostSeries(mandatoryTotal + baseline,
                distribute(month, baseline, mandatoryPerDay)));
    }

    private static List<YearMonth> trailingMonths(YearMonth month) {
        List<YearMonth> months = new ArrayList<>(TRAILING_MONTHS);
        for (int back = TRAILING_MONTHS; back >= 1; back--) {
            months.add(month.minusMonths(back));
        }
        return months;
    }

    private static Set<YearMonth> monthsCarryingData(List<ExpenseProjectionEntity> rows) {
        Set<YearMonth> months = new HashSet<>();
        rows.forEach(row -> months.add(YearMonth.from(row.getExpenseDate())));
        return months;
    }

    /**
     * Sums each category's median trailing non-mandatory month. A category absent from any
     * trailing month contributes nothing: absence is missing history, which is not the same
     * claim as a month in which the category existed and nothing discretionary was spent.
     */
    private static long baselineNonMandatorySpend(List<YearMonth> trailing,
            List<ExpenseProjectionEntity> trailingRows) {
        Map<String, Set<YearMonth>> monthsPresent = new HashMap<>();
        Map<String, Map<YearMonth, Long>> nonMandatory = new HashMap<>();

        for (ExpenseProjectionEntity row : trailingRows) {
            YearMonth rowMonth = YearMonth.from(row.getExpenseDate());
            if (!trailing.contains(rowMonth)) {
                continue;
            }
            String category = topLevelCategory(row.getCategoryPath());
            monthsPresent.computeIfAbsent(category, key -> new HashSet<>()).add(rowMonth);
            if (!row.isMandatory()) {
                nonMandatory.computeIfAbsent(category, key -> new HashMap<>())
                        .merge(rowMonth, row.getAmount(), Long::sum);
            }
        }

        long baseline = 0;
        for (Map.Entry<String, Set<YearMonth>> entry : monthsPresent.entrySet()) {
            if (!entry.getValue().containsAll(trailing)) {
                continue;
            }
            Map<YearMonth, Long> perMonth = nonMandatory.getOrDefault(entry.getKey(), Map.of());
            baseline += median(trailing.stream()
                    .map(trailingMonth -> perMonth.getOrDefault(trailingMonth, 0L))
                    .toList());
        }
        return baseline;
    }

    static long median(List<Long> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Median is undefined for an empty series");
        }
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
    }

    private static Map<LocalDate, Long> mandatoryPerDay(List<ExpenseProjectionEntity> monthRows) {
        Map<LocalDate, Long> perDay = new HashMap<>();
        monthRows.stream()
                .filter(ExpenseProjectionEntity::isMandatory)
                .forEach(row -> perDay.merge(row.getExpenseDate(), row.getAmount(), Long::sum));
        return perDay;
    }

    /**
     * Real mandatory spend keeps its actual day, while the baseline is spread evenly across
     * the month — the counterfactual makes no claim about which day discretionary money
     * would have been spent. Each day takes the difference between successive rounded
     * cumulative shares, so the daily amounts sum to the baseline exactly.
     */
    private static List<DayPointDto> distribute(YearMonth month, long baseline,
            Map<LocalDate, Long> mandatoryPerDay) {
        int days = month.lengthOfMonth();
        List<DayPointDto> byDay = new ArrayList<>(days);
        long carried = 0;
        for (int day = 1; day <= days; day++) {
            long cumulative = Math.round((double) baseline * day / days);
            LocalDate date = month.atDay(day);
            byDay.add(new DayPointDto(date,
                    mandatoryPerDay.getOrDefault(date, 0L) + cumulative - carried));
            carried = cumulative;
        }
        return List.copyOf(byDay);
    }
}
