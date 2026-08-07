package com.personalfinance.quest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.quest.dto.CalendarDto;
import com.personalfinance.quest.dto.DayStatusDto;
import com.personalfinance.quest.dto.GoalOutcomeDto;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.repository.GoalRepository;

class CalendarServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final YearMonth july = YearMonth.of(2026, 7);
    private final LocalDate today = LocalDate.of(2026, 7, 15);

    private GoalRepository goals;
    private GoalService goalService;
    private CalendarService service;

    @BeforeEach
    void setUp() {
        goals = mock(GoalRepository.class);
        goalService = mock(GoalService.class);
        service = new CalendarService(goals, goalService);
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
    }

    private GoalEntity goal(String period, long target) {
        return new GoalEntity(userId, period + " goal", null, target, period,
                LocalDate.of(2026, 1, 1), null);
    }

    private GoalOutcomeDto outcome(boolean met) {
        return new GoalOutcomeDto(UUID.randomUUID(), "g", 100, met ? 50 : 150, met);
    }

    // ---- build ----

    @Test
    void aMonthWithNoGoalsStillReturnsEveryDay() {
        CalendarDto calendar = service.build(userId, july, today);

        assertThat(calendar.month()).isEqualTo("2026-07");
        assertThat(calendar.days()).hasSize(31);
        assertThat(calendar.days()).allSatisfy(day -> {
            assertThat(day.status()).isEqualTo(DayStatusDto.NO_GOAL);
            assertThat(day.totalSpent()).isZero();
            assertThat(day.goals()).isEmpty();
        });
        assertThat(calendar.monthlyGoals()).isEmpty();
        assertThat(calendar.yearlyGoals()).isEmpty();
    }

    @Test
    void februaryInALeapYearHasTwentyNineDays() {
        // Given the shortest month, in a leap year — the day-loop boundary
        CalendarDto calendar = service.build(userId, YearMonth.of(2028, 2), LocalDate.of(2028, 2, 10));

        assertThat(calendar.days()).hasSize(29);
        assertThat(calendar.days().get(28).date()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void aDailyGoalGetsAPerDayOutcomeAndOnlyElapsedDaysArePersisted() {
        // Given one daily goal of 100 bani and a single 50-bani day
        GoalEntity daily = goal("DAILY", 100);
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(daily));
        when(goalService.dailyActuals(any(), any(), any()))
                .thenReturn(java.util.Map.of(LocalDate.of(2026, 7, 3), 50L));

        CalendarDto calendar = service.build(userId, july, today);

        // Then every day carries the goal's outcome
        assertThat(calendar.days()).allSatisfy(day -> assertThat(day.goals()).hasSize(1));
        // and only the 14 days strictly before today are written to history
        verify(goalService, org.mockito.Mockito.times(14))
                .recordEvaluation(any(), any(), any(), anyLong(), anyBoolean());
    }

    @Test
    void aMonthlyGoalStillRunningIsReportedInProgressAndNotPersisted() {
        // Given a monthly goal and a "today" inside the month
        GoalEntity monthly = goal("MONTHLY", 100000);
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(monthly));
        when(goalService.actualFor(any(), any(), any())).thenReturn(40000L);

        CalendarDto calendar = service.build(userId, july, today);

        // Then it is summarized as open, and no evaluation row is written for an unfinished period
        assertThat(calendar.monthlyGoals()).singleElement().satisfies(summary -> {
            assertThat(summary.inProgress()).isTrue();
            assertThat(summary.actual()).isEqualTo(40000L);
            assertThat(summary.met()).isTrue();
        });
        verify(goalService, never()).recordEvaluation(any(), any(), any(), anyLong(), anyBoolean());
    }

    @Test
    void aFinishedMonthlyGoalIsPersistedOnceTheMonthIsOver() {
        // Given "today" is in August but the calendar is asked for July
        GoalEntity monthly = goal("MONTHLY", 10000);
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(monthly));
        when(goalService.actualFor(any(), any(), any())).thenReturn(99999L);

        CalendarDto calendar = service.build(userId, july, LocalDate.of(2026, 8, 5));

        // Then the closed period is recorded and reported as missed
        assertThat(calendar.monthlyGoals()).singleElement().satisfies(summary -> {
            assertThat(summary.inProgress()).isFalse();
            assertThat(summary.met()).isFalse();
        });
        verify(goalService).recordEvaluation(monthly, LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31), 99999L, false);
    }

    @Test
    void aYearlyGoalIsSummarizedOverTheWholeYearNotTheMonth() {
        GoalEntity yearly = goal("YEARLY", 1000000);
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(yearly));
        when(goalService.actualFor(any(), any(), any())).thenReturn(500000L);

        CalendarDto calendar = service.build(userId, july, today);

        assertThat(calendar.yearlyGoals()).hasSize(1);
        verify(goalService).actualFor(yearly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    @Test
    void aGoalOutsideItsDateRangeIsExcludedFromTheMonth() {
        // Given a monthly goal that ended in May
        GoalEntity ended = new GoalEntity(userId, "Spring only", null, 1000, "MONTHLY",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31));
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(ended));

        CalendarDto calendar = service.build(userId, july, today);

        assertThat(calendar.monthlyGoals()).isEmpty();
    }

    // ---- dayStatus ----

    @Test
    void dayStatusIsNoGoalWhenNothingApplies() {
        assertThat(CalendarService.dayStatus(List.of(), LocalDate.of(2026, 7, 3), today))
                .isEqualTo(DayStatusDto.NO_GOAL);
    }

    @Test
    void dayStatusDistinguishesPastTodayAndFuture() {
        List<GoalOutcomeDto> met = List.of(outcome(true));

        assertThat(CalendarService.dayStatus(met, LocalDate.of(2026, 7, 14), today))
                .isEqualTo(DayStatusDto.MET);
        assertThat(CalendarService.dayStatus(met, today, today))
                .isEqualTo(DayStatusDto.IN_PROGRESS);
        assertThat(CalendarService.dayStatus(met, LocalDate.of(2026, 7, 16), today))
                .isEqualTo(DayStatusDto.FUTURE);
    }

    @Test
    void aSingleMissedGoalMakesTheWholeDayMissed() {
        // Given an elapsed day where one of two goals was missed
        List<GoalOutcomeDto> mixed = List.of(outcome(true), outcome(false));

        // Then the day reads as missed — "all goals met" is the bar
        assertThat(CalendarService.dayStatus(mixed, LocalDate.of(2026, 7, 14), today))
                .isEqualTo(DayStatusDto.MISSED);
    }
}
