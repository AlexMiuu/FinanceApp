package com.personalfinance.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.report.dto.CategorySliceDto;
import com.personalfinance.report.dto.DashboardDto;
import com.personalfinance.report.entity.ExpenseProjectionEntity;
import com.personalfinance.report.repository.ExpenseProjectionRepository;

class DashboardServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final ExpenseProjectionRepository repo = mock(ExpenseProjectionRepository.class);
    private final DashboardService service = new DashboardService(repo);

    private ExpenseProjectionEntity row(String path, boolean mandatory, long amount, LocalDate date) {
        return new ExpenseProjectionEntity(UUID.randomUUID(), userId, UUID.randomUUID(), path,
                mandatory, amount, "RON", null, date);
    }

    private void stubMonth(YearMonth month, List<ExpenseProjectionEntity> rows) {
        when(repo.findByUserIdAndExpenseDateBetween(userId, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(rows);
    }

    private void stubTrailingWindow(YearMonth month, List<ExpenseProjectionEntity> rows) {
        when(repo.findByUserIdAndExpenseDateBetween(userId,
                month.minusMonths(3).atDay(1), month.minusMonths(1).atEndOfMonth()))
                .thenReturn(rows);
    }

    @Test
    void aggregatesMonth() {
        YearMonth july = YearMonth.of(2026, 7);
        stubMonth(july, List.of(
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 7, 1)),
                row("Food > Groceries", false, 4550, LocalDate.of(2026, 7, 10)),
                row("Food > Restaurants", false, 3000, LocalDate.of(2026, 7, 10))));
        when(repo.sumForRange(any(), any(), any())).thenReturn(150000L);

        DashboardDto dashboard = service.build(userId, july, LocalDate.of(2026, 7, 15));

        assertThat(dashboard.totalSpent()).isEqualTo(207550);
        assertThat(dashboard.mandatorySpent()).isEqualTo(200000);
        assertThat(dashboard.expenseCount()).isEqualTo(3);
        assertThat(dashboard.previousMonthTotal()).isEqualTo(150000);
        // top-level grouping: Food = 4550 + 3000
        assertThat(dashboard.byCategory()).containsExactly(
                new CategorySliceDto("Food", 7550),
                new CategorySliceDto("Housing", 200000));
        // linear projection: 207550 / 15 * 31
        assertThat(dashboard.projectedMonthEnd()).isEqualTo(Math.round(207550.0 / 15 * 31));
        // every day of the month is present, zero-filled
        assertThat(dashboard.byDay()).hasSize(31);
        assertThat(dashboard.byDay().get(9).amount()).isEqualTo(7550);
    }

    @Test
    void noProjectionForPastMonths() {
        YearMonth june = YearMonth.of(2026, 6);
        when(repo.findByUserIdAndExpenseDateBetween(any(), any(), any())).thenReturn(List.of());
        when(repo.sumForRange(any(), any(), any())).thenReturn(0L);

        DashboardDto dashboard = service.build(userId, june, LocalDate.of(2026, 7, 15));

        assertThat(dashboard.projectedMonthEnd()).isNull();
    }

    @Test
    void nullMonthComponentsFailFastOnBuild() {
        // build() requires a non-null month; passing null surfaces immediately rather than
        // silently defaulting, since a report with no month is not a valid request.
        assertThatThrownBy(() -> service.build(userId, null, LocalDate.of(2026, 7, 15)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullUserOrTodayFailFastOnBuild() {
        assertThatThrownBy(() -> service.build(null, YearMonth.of(2026, 7), LocalDate.of(2026, 7, 15)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.build(userId, YearMonth.of(2026, 7), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ghostIsAbsentForAUserWithoutThreeTrailingMonths() {
        YearMonth august = YearMonth.of(2026, 8);
        stubMonth(august, List.of(row("Food > Groceries", false, 5000, LocalDate.of(2026, 8, 2))));
        stubTrailingWindow(august, List.of(
                row("Food > Groceries", false, 4000, LocalDate.of(2026, 6, 2)),
                row("Food > Groceries", false, 6000, LocalDate.of(2026, 7, 2))));
        when(repo.sumForRange(any(), any(), any())).thenReturn(6000L);

        DashboardDto dashboard = service.build(userId, august, LocalDate.of(2026, 8, 5));

        assertThat(dashboard.ghostByDay()).isNull();
        assertThat(dashboard.ghostMonthTotal()).isNull();
    }

    /**
     * Same hand-worked fixture as {@code GhostFlockCalculatorTest}, exercised through the
     * service so the trailing window the repository is asked for is covered too:
     * Food median 70000 + Fun median 25000 = 95000 baseline, plus 200000 real mandatory rent.
     */
    @Test
    void ghostIsPresentAndReconcilesOnceThreeTrailingMonthsCarryData() {
        YearMonth august = YearMonth.of(2026, 8);
        stubMonth(august, List.of(
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 8, 1)),
                row("Food > Groceries", false, 5000, LocalDate.of(2026, 8, 10))));
        stubTrailingWindow(august, List.of(
                row("Food > Groceries", false, 30000, LocalDate.of(2026, 5, 3)),
                row("Food > Restaurants", false, 20000, LocalDate.of(2026, 5, 19)),
                row("Food > Groceries", false, 90000, LocalDate.of(2026, 6, 7)),
                row("Food > Groceries", false, 70000, LocalDate.of(2026, 7, 11)),
                row("Fun > Cinema", false, 10000, LocalDate.of(2026, 5, 22)),
                row("Fun > Cinema", false, 40000, LocalDate.of(2026, 6, 14)),
                row("Fun > Cinema", false, 25000, LocalDate.of(2026, 7, 25)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 5, 1)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 6, 1)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 7, 1))));
        when(repo.sumForRange(any(), any(), any())).thenReturn(295000L);

        DashboardDto dashboard = service.build(userId, august, LocalDate.of(2026, 8, 10));

        assertThat(dashboard.ghostMonthTotal()).isEqualTo(295000);
        assertThat(dashboard.ghostByDay()).hasSize(31);
        assertThat(dashboard.ghostByDay().stream().mapToLong(day -> day.amount()).sum())
                .isEqualTo(295000);
        // The real month is far cheaper than the baseline, which is the point of the ghost.
        assertThat(dashboard.totalSpent()).isEqualTo(205000);
    }
}
