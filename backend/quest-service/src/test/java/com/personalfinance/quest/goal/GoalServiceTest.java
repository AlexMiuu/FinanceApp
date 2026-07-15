package com.personalfinance.quest.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.quest.domain.CategoryProjectionEntity;
import com.personalfinance.quest.domain.CategoryProjectionRepository;
import com.personalfinance.quest.domain.ExpenseProjectionEntity;
import com.personalfinance.quest.domain.ExpenseProjectionRepository;

class GoalServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID food = UUID.randomUUID();
    private final UUID groceries = UUID.randomUUID();   // child of food
    private final UUID transport = UUID.randomUUID();

    private ExpenseProjectionRepository expenses;
    private CategoryProjectionRepository categories;
    private GoalService service;

    @BeforeEach
    void setUp() {
        expenses = mock(ExpenseProjectionRepository.class);
        categories = mock(CategoryProjectionRepository.class);
        service = new GoalService(mock(GoalRepository.class), mock(GoalEvaluationRepository.class),
                expenses, categories);

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
    void goalAppliesOnlyWithinItsDates() {
        GoalEntity goal = new GoalEntity(userId, "Summer", null, 1000, "DAILY",
                LocalDate.parse("2026-06-01"), LocalDate.parse("2026-08-31"));

        assertThat(goal.appliesOn(LocalDate.parse("2026-05-31"))).isFalse();
        assertThat(goal.appliesOn(LocalDate.parse("2026-07-15"))).isTrue();
        assertThat(goal.appliesOn(LocalDate.parse("2026-09-01"))).isFalse();
    }
}
