package com.personalfinance.quest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.quest.entity.CategoryProjectionEntity;
import com.personalfinance.quest.entity.ExpenseProjectionEntity;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.entity.GoalEvaluationEntity;
import com.personalfinance.quest.exception.NotFoundException;
import com.personalfinance.quest.repository.CategoryProjectionRepository;
import com.personalfinance.quest.repository.ExpenseProjectionRepository;
import com.personalfinance.quest.repository.GoalEvaluationRepository;
import com.personalfinance.quest.repository.GoalRepository;

class GoalServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID food = UUID.randomUUID();
    private final UUID groceries = UUID.randomUUID();   // child of food
    private final UUID transport = UUID.randomUUID();

    private GoalRepository goals;
    private GoalEvaluationRepository evaluations;
    private ExpenseProjectionRepository expenses;
    private CategoryProjectionRepository categories;
    private GoalService service;

    @BeforeEach
    void setUp() {
        goals = mock(GoalRepository.class);
        evaluations = mock(GoalEvaluationRepository.class);
        expenses = mock(ExpenseProjectionRepository.class);
        categories = mock(CategoryProjectionRepository.class);
        service = new GoalService(goals, evaluations, expenses, categories);

        when(expenses.findByUserIdAndExpenseDateBetween(any(), any(), any())).thenReturn(List.of(
                row(food, 1000, "2026-07-01"),
                row(groceries, 2000, "2026-07-02"),
                row(transport, 5000, "2026-07-03")));
        when(categories.findByParentId(food)).thenReturn(List.of(
                new CategoryProjectionEntity(groceries, userId, "Groceries", food, false)));
    }

    private ExpenseProjectionEntity row(UUID categoryId, long amount, String date) {
        return new ExpenseProjectionEntity(UUID.randomUUID(), userId, categoryId, "path", false,
                amount, "RON", null, LocalDate.parse(date));
    }

    private GoalEntity goal(UUID categoryId, long target, String period) {
        return new GoalEntity(userId, "Budget", categoryId, target, period,
                LocalDate.parse("2026-01-01"), null);
    }

    // ---- actualFor ----

    @Test
    void overallGoalSumsEverything() {
        GoalEntity goal = new GoalEntity(userId, "All spending", null, 10000, "MONTHLY",
                LocalDate.parse("2026-01-01"), null);

        assertThat(service.actualFor(goal, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31")))
                .isEqualTo(8000);
    }

    @Test
    void categoryGoalIncludesSubcategories() {
        GoalEntity goal = new GoalEntity(userId, "Food budget", food, 10000, "MONTHLY",
                LocalDate.parse("2026-01-01"), null);

        // food (1000) + groceries child (2000), transport excluded
        assertThat(service.actualFor(goal, LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31")))
                .isEqualTo(3000);
    }

    @Test
    void actualForIsZeroWhenTheProjectionHasNoRowsInTheWindow() {
        // Given a user whose projection is empty for the requested window
        when(expenses.findByUserIdAndExpenseDateBetween(any(), any(), any())).thenReturn(List.of());

        // When the goal is evaluated
        long actual = service.actualFor(goal(null, 10000, "MONTHLY"),
                LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"));

        // Then it reports zero rather than failing on the empty stream
        assertThat(actual).isZero();
    }

    // ---- period arithmetic ----

    @Test
    void periodBoundaries() {
        LocalDate date = LocalDate.parse("2026-07-15");

        assertThat(GoalService.periodStart("DAILY", date)).isEqualTo(date);
        assertThat(GoalService.periodEnd("DAILY", date)).isEqualTo(date);
        assertThat(GoalService.periodStart("MONTHLY", date)).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(GoalService.periodEnd("MONTHLY", date)).isEqualTo(LocalDate.parse("2026-07-31"));
        assertThat(GoalService.periodStart("YEARLY", date)).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(GoalService.periodEnd("YEARLY", date)).isEqualTo(LocalDate.parse("2026-12-31"));
    }

    @Test
    void periodEndHandlesLeapYearFebruary() {
        // Given the last day of a leap February
        LocalDate leapFeb = LocalDate.parse("2028-02-10");

        // When the monthly period is computed
        // Then it ends on the 29th, not the 28th
        assertThat(GoalService.periodEnd("MONTHLY", leapFeb)).isEqualTo(LocalDate.parse("2028-02-29"));
        assertThat(GoalService.periodEnd("YEARLY", leapFeb)).isEqualTo(LocalDate.parse("2028-12-31"));
    }

    @Test
    void unknownPeriodIsRejectedRatherThanSilentlyDefaulted() {
        assertThatThrownBy(() -> GoalService.periodStart("FORTNIGHTLY", LocalDate.parse("2026-07-15")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORTNIGHTLY");
        assertThatThrownBy(() -> GoalService.periodEnd("FORTNIGHTLY", LocalDate.parse("2026-07-15")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void goalAppliesOnlyWithinItsDates() {
        GoalEntity goal = new GoalEntity(userId, "Summer", null, 1000, "DAILY",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-08-31"));

        assertThat(goal.appliesOn(LocalDate.parse("2026-05-31"))).isFalse();
        assertThat(goal.appliesOn(LocalDate.parse("2026-07-15"))).isTrue();
        assertThat(goal.appliesOn(LocalDate.parse("2026-09-01"))).isFalse();
    }

    // ---- list ----

    @Test
    void listReturnsAnEmptyListForAUserWithNoGoals() {
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        assertThat(service.list(userId, LocalDate.parse("2026-07-15"))).isEmpty();
    }

    @Test
    void listMarksAGoalMetExactlyAtItsTarget() {
        // Given a goal whose target equals the period's actual (8000) — the boundary
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(goal(null, 8000, "MONTHLY")));

        List<GoalStatus> statuses = service.list(userId, LocalDate.parse("2026-07-15"));

        // Then "met" is inclusive: actual <= target
        assertThat(statuses).singleElement().satisfies(status -> {
            assertThat(status.currentActual()).isEqualTo(8000);
            assertThat(status.currentMet()).isTrue();
        });
    }

    @Test
    void listMarksAGoalMissedOneBaniOverItsTarget() {
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(goal(null, 7999, "MONTHLY")));

        assertThat(service.list(userId, LocalDate.parse("2026-07-15")))
                .singleElement()
                .satisfies(status -> assertThat(status.currentMet()).isFalse());
    }

    // ---- get / update / delete ----

    @Test
    void getReturnsTheGoalWithItsCurrentPeriodProgress() {
        GoalEntity goal = goal(null, 10000, "MONTHLY");
        when(goals.findByIdAndUserId(goal.getId(), userId)).thenReturn(Optional.of(goal));

        GoalStatus status = service.get(goal.getId(), userId, LocalDate.parse("2026-07-15"));

        assertThat(status.goal()).isSameAs(goal);
        assertThat(status.periodStart()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(status.periodEnd()).isEqualTo(LocalDate.parse("2026-07-31"));
    }

    @Test
    void getThrowsNotFoundForAGoalBelongingToAnotherUser() {
        UUID id = UUID.randomUUID();
        when(goals.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id, userId, LocalDate.parse("2026-07-15")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Goal not found");
    }

    @Test
    void updateThrowsNotFoundInsteadOfCreatingAGoal() {
        UUID id = UUID.randomUUID();
        when(goals.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, userId, "New", null, 100, "DAILY",
                LocalDate.parse("2026-01-01"), null, true))
                .isInstanceOf(NotFoundException.class);
        verify(goals, never()).save(any());
    }

    @Test
    void deleteThrowsNotFoundForAGoalBelongingToAnotherUser() {
        UUID id = UUID.randomUUID();
        when(goals.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, userId)).isInstanceOf(NotFoundException.class);
        verify(goals, never()).delete(any());
    }

    // ---- recordEvaluation ----

    @Test
    void recordEvaluationInsertsWhenNoRowExistsForThePeriod() {
        GoalEntity goal = goal(null, 10000, "DAILY");
        LocalDate day = LocalDate.parse("2026-07-14");
        when(evaluations.findByGoalIdAndPeriodStart(goal.getId(), day)).thenReturn(Optional.empty());

        service.recordEvaluation(goal, day, day, 500, true);

        verify(evaluations).save(any(GoalEvaluationEntity.class));
    }

    @Test
    void recordEvaluationRefreshesTheExistingRowRatherThanDuplicatingIt() {
        // Given an evaluation already stored for this goal + period
        GoalEntity goal = goal(null, 10000, "DAILY");
        LocalDate day = LocalDate.parse("2026-07-14");
        GoalEvaluationEntity existing = new GoalEvaluationEntity(goal.getId(), day, day, 100, true);
        when(evaluations.findByGoalIdAndPeriodStart(goal.getId(), day)).thenReturn(Optional.of(existing));

        // When the period is re-evaluated with a different outcome
        service.recordEvaluation(goal, day, day, 999, false);

        // Then the stored row is updated in place — the UNIQUE(goal_id, period_start)
        // constraint means a second insert would fail
        assertThat(existing.getActualAmount()).isEqualTo(999);
        assertThat(existing.isMet()).isFalse();
        verify(evaluations).save(existing);
    }
}
