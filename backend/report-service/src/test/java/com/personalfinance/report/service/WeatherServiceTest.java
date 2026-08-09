package com.personalfinance.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.report.dto.WeatherDto;
import com.personalfinance.report.entity.UserIncomeEntity;
import com.personalfinance.report.entity.WeatherBand;
import com.personalfinance.report.entity.WeatherStateEntity;
import com.personalfinance.report.events.Events;
import com.personalfinance.report.mapper.WeatherMapper;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.UserIncomeRepository;
import com.personalfinance.report.repository.WeatherStateRepository;

/**
 * Given-When-Then coverage of the hysteresis state machine (D4). The
 * repository mocks stand in for a single persisted row per user, mutated
 * across successive {@code recompute} calls, mirroring how the real
 * {@link WeatherStateRepository} would behave for one user across an hourly
 * sweep.
 */
class WeatherServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-08-01T00:00:00Z");

    private ExpenseProjectionRepository expenses;
    private UserIncomeRepository incomes;
    private WeatherStateRepository states;
    private List<Object> published;
    private WeatherService service;

    /** In-memory stand-in so successive recompute() calls see prior saves, like a real row. */
    private WeatherStateEntity persisted;

    @BeforeEach
    void setUp() {
        expenses = mock(ExpenseProjectionRepository.class);
        incomes = mock(UserIncomeRepository.class);
        states = mock(WeatherStateRepository.class);
        published = new ArrayList<>();
        persisted = null;

        when(states.findById(userId)).thenAnswer(inv -> Optional.ofNullable(persisted));
        when(states.save(any(WeatherStateEntity.class))).thenAnswer(inv -> {
            persisted = inv.getArgument(0);
            return persisted;
        });

        service = new WeatherService(expenses, incomes, states, new WeatherMapper(), published::add);
    }

    private void givenIncome(long monthlyIncome) {
        when(incomes.findById(userId)).thenReturn(Optional.of(new UserIncomeEntity(userId, monthlyIncome)));
    }

    private void givenRatio(double ratio, long monthlyIncome) {
        givenIncome(monthlyIncome);
        // burnRate / monthlyIncome == ratio  =>  burnRate == ratio * monthlyIncome
        when(expenses.sumForRange(any(), any(), any())).thenReturn(Math.round(ratio * monthlyIncome));
    }

    @Test
    void recomputeThrowsOnNullUserId() {
        assertThatThrownBy(() -> service.recompute(null, t0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recomputeThrowsOnNullNow() {
        assertThatThrownBy(() -> service.recompute(userId, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currentForThrowsOnNullUserId() {
        assertThatThrownBy(() -> service.currentFor(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void currentForDefaultsToClearWhenNoStateRowExists() {
        WeatherDto dto = service.currentFor(userId);

        assertThat(dto.band()).isEqualTo("clear");
    }

    @Test
    void missingIncomeStaysClearWithNoEventAndNoRowCreated() {
        when(incomes.findById(userId)).thenReturn(Optional.empty());

        service.recompute(userId, t0);

        assertThat(published).isEmpty();
        verify(states, never()).save(any());
        assertThat(service.currentFor(userId).band()).isEqualTo("clear");
    }

    @Test
    void zeroIncomeStaysClearWithNoEventAndNoRowCreated() {
        givenRatio(1.5, 0);
        when(incomes.findById(userId)).thenReturn(Optional.of(new UserIncomeEntity(userId, 0)));

        service.recompute(userId, t0);

        assertThat(published).isEmpty();
        verify(states, never()).save(any());
    }

    @Test
    void firstCrossingStartsPendingWithoutCommitting() {
        givenRatio(0.9, 1_000_00); // ratio 0.9 >= 0.85 -> candidate GATHERING, current CLEAR

        service.recompute(userId, t0);

        assertThat(published).isEmpty();
        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.CLEAR);
        assertThat(persisted.getPendingBand()).contains(WeatherBand.GATHERING);
        assertThat(persisted.getPendingSince()).contains(t0);
    }

    @Test
    void pendingCommitsAtExactlySixHours() {
        givenRatio(0.9, 1_000_00);
        service.recompute(userId, t0); // starts pending at t0

        service.recompute(userId, t0.plus(6, ChronoUnit.HOURS));

        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.GATHERING);
        assertThat(persisted.getPendingBand()).isEmpty();
        assertThat(published).singleElement().isInstanceOfSatisfying(Events.WeatherUpdated.class, event -> {
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.band()).isEqualTo("gathering");
            assertThat(event.routingKey()).isEqualTo("ambient.weather.updated");
        });
    }

    @Test
    void pendingDoesNotCommitOneSecondBeforeSixHours() {
        givenRatio(0.9, 1_000_00);
        service.recompute(userId, t0);

        service.recompute(userId, t0.plus(6, ChronoUnit.HOURS).minusSeconds(1));

        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.CLEAR);
        assertThat(persisted.getPendingBand()).contains(WeatherBand.GATHERING);
        assertThat(published).isEmpty();
    }

    @Test
    void oscillatingAcrossTheBoundaryWithinTheDwellWindowProducesZeroCommits() {
        // 0.9 (candidate GATHERING, starts pending) then 0.7 (candidate == current
        // CLEAR, cancels pending) repeated — the clock never accumulates.
        Instant t = t0;
        for (int i = 0; i < 8; i++) {
            givenRatio(i % 2 == 0 ? 0.9 : 0.7, 1_000_00);
            service.recompute(userId, t);
            t = t.plus(1, ChronoUnit.HOURS);
        }

        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.CLEAR);
        assertThat(published).isEmpty();
    }

    @Test
    void sustainedElevatedRatioAccumulatesAcrossRecomputesAndCommitsOnce() {
        // Same candidate (GATHERING) on every call — the pending clock must
        // keep counting from the first crossing, not reset on each recompute.
        givenRatio(0.9, 1_000_00);
        service.recompute(userId, t0);
        service.recompute(userId, t0.plus(3, ChronoUnit.HOURS));
        service.recompute(userId, t0.plus(6, ChronoUnit.HOURS));

        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.GATHERING);
        assertThat(published).hasSize(1);
    }

    @Test
    void fallingBackFromGatheringToClearNeedsTheShiftedLowerBoundary() {
        givenRatio(0.9, 1_000_00);
        service.recompute(userId, t0);
        service.recompute(userId, t0.plus(6, ChronoUnit.HOURS)); // commits to GATHERING
        published.clear();

        // 0.8 is below the raw 0.8 band line but above the shifted 0.75 fallback
        // threshold, so GATHERING must hold, not fall back to CLEAR.
        givenRatio(0.8, 1_000_00);
        service.recompute(userId, t0.plus(7, ChronoUnit.HOURS));

        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.GATHERING);
        assertThat(persisted.getPendingBand()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void climbingFromGatheringToStormNeedsTheShiftedUpperBoundaryAndDwell() {
        givenRatio(0.9, 1_000_00);
        service.recompute(userId, t0);
        service.recompute(userId, t0.plus(6, ChronoUnit.HOURS)); // commits to GATHERING
        published.clear();

        givenRatio(1.2, 1_000_00); // > 1.05 -> candidate STORM
        service.recompute(userId, t0.plus(7, ChronoUnit.HOURS));
        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.GATHERING);
        assertThat(published).isEmpty();

        service.recompute(userId, t0.plus(13, ChronoUnit.HOURS));
        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.STORM);
        assertThat(published).singleElement().isInstanceOfSatisfying(Events.WeatherUpdated.class,
                event -> assertThat(event.band()).isEqualTo("storm"));
    }

    @Test
    void degradedComputationHoldsTheCommittedBandAndCancelsAnyPending() {
        givenRatio(0.9, 1_000_00);
        service.recompute(userId, t0); // starts pending toward GATHERING

        when(incomes.findById(userId)).thenReturn(Optional.empty()); // income now missing
        service.recompute(userId, t0.plus(1, ChronoUnit.HOURS));

        assertThat(persisted.getCurrentBand()).isEqualTo(WeatherBand.CLEAR);
        assertThat(persisted.getPendingBand()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void degradedComputationWithNoExistingRowSavesNothing() {
        when(incomes.findById(userId)).thenReturn(Optional.empty());

        service.recompute(userId, t0);

        verify(states, never()).save(any());
        assertThat(persisted).isNull();
    }
}
