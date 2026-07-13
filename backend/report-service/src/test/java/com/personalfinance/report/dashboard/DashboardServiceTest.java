package com.personalfinance.report.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.report.domain.ExpenseProjectionEntity;
import com.personalfinance.report.domain.ExpenseProjectionRepository;

class DashboardServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final ExpenseProjectionRepository repo = mock(ExpenseProjectionRepository.class);
    private final DashboardService service = new DashboardService(repo);

    private ExpenseProjectionEntity row(String path, boolean mandatory, long amount, LocalDate date) {
        return new ExpenseProjectionEntity(UUID.randomUUID(), userId, UUID.randomUUID(), path,
                mandatory, amount, "RON", null, date);
    }

    @Test
    void aggregatesMonth() {
        YearMonth july = YearMonth.of(2026, 7);
        when(repo.findByUserIdAndExpenseDateBetween(userId, july.atDay(1), july.atEndOfMonth()))
                .thenReturn(List.of(
                        row("Housing > Rent", true, 200000, LocalDate.of(2026, 7, 1)),
                        row("Food > Groceries", false, 4550, LocalDate.of(2026, 7, 10)),
                        row("Food > Restaurants", false, 3000, LocalDate.of(2026, 7, 10))));
        when(repo.sumForRange(any(), any(), any())).thenReturn(150000L);

        DashboardService.Dashboard dashboard = service.build(userId, july, LocalDate.of(2026, 7, 15));

        assertThat(dashboard.totalSpent()).isEqualTo(207550);
        assertThat(dashboard.mandatorySpent()).isEqualTo(200000);
        assertThat(dashboard.expenseCount()).isEqualTo(3);
        assertThat(dashboard.previousMonthTotal()).isEqualTo(150000);
        // top-level grouping: Food = 4550 + 3000
        assertThat(dashboard.byCategory()).containsExactly(
                new DashboardService.CategorySlice("Food", 7550),
                new DashboardService.CategorySlice("Housing", 200000));
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

        DashboardService.Dashboard dashboard = service.build(userId, june, LocalDate.of(2026, 7, 15));

        assertThat(dashboard.projectedMonthEnd()).isNull();
    }
}
