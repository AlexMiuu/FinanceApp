package com.personalfinance.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.report.entity.ExpenseProjectionEntity;

class GhostFlockCalculatorTest {

    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    private final UUID userId = UUID.randomUUID();

    private ExpenseProjectionEntity row(String path, boolean mandatory, long amount, LocalDate date) {
        return new ExpenseProjectionEntity(UUID.randomUUID(), userId, UUID.randomUUID(), path,
                mandatory, amount, "RON", null, date);
    }

    @Test
    void medianOfOddSeriesIsTheMiddleValue() {
        assertThat(GhostFlockCalculator.median(List.of(30L, 10L, 20L))).isEqualTo(20L);
    }

    @Test
    void medianOfEvenSeriesAveragesTheTwoMiddleValues() {
        assertThat(GhostFlockCalculator.median(List.of(40L, 10L, 30L, 20L))).isEqualTo(25L);
    }

    @Test
    void medianOfEvenSeriesRoundsHalfUpToWholeBani() {
        // (10 + 15) / 2 = 12.5 bani, which cannot be stored as a fraction of a ban.
        assertThat(GhostFlockCalculator.median(List.of(10L, 15L))).isEqualTo(13L);
    }

    @Test
    void medianOfSingleValueSeriesIsThatValue() {
        assertThat(GhostFlockCalculator.median(List.of(7L))).isEqualTo(7L);
    }

    @Test
    void medianOfEmptySeriesIsRejected() {
        assertThatThrownBy(() -> GhostFlockCalculator.median(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("undefined");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> GhostFlockCalculator.compute(null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GhostFlockCalculator.compute(AUGUST, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> GhostFlockCalculator.compute(AUGUST, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exactlyThreeTrailingMonthsOfHistoryProducesAGhost() {
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Food > Groceries", false, 1000, LocalDate.of(2026, 5, 4)),
                row("Food > Groceries", false, 1000, LocalDate.of(2026, 6, 4)),
                row("Food > Groceries", false, 1000, LocalDate.of(2026, 7, 4)));

        assertThat(GhostFlockCalculator.compute(AUGUST, trailing, List.of())).isPresent();
    }

    @Test
    void onlyTwoTrailingMonthsOfHistoryProducesNoGhost() {
        // The boundary below the one above: June and July carry data, May does not.
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Food > Groceries", false, 1000, LocalDate.of(2026, 6, 4)),
                row("Food > Groceries", false, 1000, LocalDate.of(2026, 7, 4)));

        assertThat(GhostFlockCalculator.compute(AUGUST, trailing, List.of())).isEmpty();
    }

    @Test
    void noHistoryAtAllProducesNoGhost() {
        assertThat(GhostFlockCalculator.compute(AUGUST, List.of(), List.of())).isEmpty();
    }

    /**
     * Hand-worked fixture, all figures in bani.
     *
     * <p>Trailing non-mandatory sums — Food: May 50000, Jun 90000, Jul 70000 → median 70000.
     * Fun: May 10000, Jun 40000, Jul 25000 → median 25000. Housing is present in all three
     * months but is entirely mandatory, so its median is 0. Baseline = 70000 + 25000 = 95000.
     *
     * <p>August real mandatory spend is 200000 (rent on the 1st), which the ghost carries
     * unchanged, so the ghost month totals 200000 + 95000 = 295000. Day 1 takes the rent plus
     * the first even share of the baseline: 200000 + round(95000 / 31) = 200000 + 3065.
     */
    @Test
    void ghostReconcilesAgainstAHandComputedMedian() {
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Food > Groceries", false, 30000, LocalDate.of(2026, 5, 3)),
                row("Food > Restaurants", false, 20000, LocalDate.of(2026, 5, 19)),
                row("Food > Groceries", false, 90000, LocalDate.of(2026, 6, 7)),
                row("Food > Groceries", false, 70000, LocalDate.of(2026, 7, 11)),
                row("Fun > Cinema", false, 10000, LocalDate.of(2026, 5, 22)),
                row("Fun > Cinema", false, 40000, LocalDate.of(2026, 6, 14)),
                row("Fun > Cinema", false, 25000, LocalDate.of(2026, 7, 25)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 5, 1)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 6, 1)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 7, 1)));
        List<ExpenseProjectionEntity> august = List.of(
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 8, 1)),
                row("Food > Groceries", false, 5000, LocalDate.of(2026, 8, 10)));

        GhostFlockCalculator.GhostSeries ghost =
                GhostFlockCalculator.compute(AUGUST, trailing, august).orElseThrow();

        assertThat(ghost.monthTotal()).isEqualTo(295000);
        assertThat(ghost.byDay()).hasSize(31);
        assertThat(ghost.byDay().get(0).amount()).isEqualTo(203065);
        assertThat(ghost.byDay().stream().mapToLong(day -> day.amount()).sum()).isEqualTo(295000);
    }

    @Test
    void aCategoryMissingFromOneTrailingMonthContributesNothing() {
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 5, 3)),
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 6, 3)),
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 7, 3)),
                // Travel appears only in July, so it has no trailing normal to be held at.
                row("Travel > Flights", false, 500000, LocalDate.of(2026, 7, 9)));

        GhostFlockCalculator.GhostSeries ghost =
                GhostFlockCalculator.compute(AUGUST, trailing, List.of()).orElseThrow();

        assertThat(ghost.monthTotal()).isEqualTo(10000);
    }

    @Test
    void aCategoryPresentButWhollyMandatoryContributesZeroRatherThanBeingDropped() {
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 5, 1)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 6, 1)),
                row("Housing > Rent", true, 200000, LocalDate.of(2026, 7, 1)));

        GhostFlockCalculator.GhostSeries ghost =
                GhostFlockCalculator.compute(AUGUST, trailing, List.of()).orElseThrow();

        assertThat(ghost.monthTotal()).isZero();
        assertThat(ghost.byDay()).allSatisfy(day -> assertThat(day.amount()).isZero());
    }

    @Test
    void rowsOutsideTheTrailingWindowAreIgnored() {
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 5, 3)),
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 6, 3)),
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 7, 3)),
                row("Food > Groceries", false, 999999, LocalDate.of(2026, 4, 3)));

        GhostFlockCalculator.GhostSeries ghost =
                GhostFlockCalculator.compute(AUGUST, trailing, List.of()).orElseThrow();

        assertThat(ghost.monthTotal()).isEqualTo(10000);
    }

    @Test
    void theBaselineIsSpreadEvenlyAcrossAShorterMonth() {
        YearMonth february = YearMonth.of(2026, 2);
        List<ExpenseProjectionEntity> trailing = List.of(
                row("Food > Groceries", false, 2800, LocalDate.of(2025, 11, 3)),
                row("Food > Groceries", false, 2800, LocalDate.of(2025, 12, 3)),
                row("Food > Groceries", false, 2800, LocalDate.of(2026, 1, 3)));

        GhostFlockCalculator.GhostSeries ghost =
                GhostFlockCalculator.compute(february, trailing, List.of()).orElseThrow();

        assertThat(ghost.byDay()).hasSize(28);
        assertThat(ghost.byDay()).allSatisfy(day -> assertThat(day.amount()).isEqualTo(100));
        assertThat(ghost.byDay().stream().mapToLong(day -> day.amount()).sum()).isEqualTo(2800);
    }
}
