package com.personalfinance.quest.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.quest.entity.CategoryProjectionEntity;
import com.personalfinance.quest.entity.ExpenseProjectionEntity;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.entity.GoalEvaluationEntity;
import com.personalfinance.quest.exception.NotFoundException;
import com.personalfinance.quest.repository.CategoryProjectionRepository;
import com.personalfinance.quest.repository.ExpenseProjectionRepository;
import com.personalfinance.quest.repository.GoalEvaluationRepository;
import com.personalfinance.quest.repository.GoalRepository;

/**
 * Spending-limit goals evaluated against the expense projection (FR-7).
 * "Met" for a spending limit means actual &lt;= target within the period.
 */
@Service
public class GoalService {

    private final GoalRepository goals;
    private final GoalEvaluationRepository evaluations;
    private final ExpenseProjectionRepository expenses;
    private final CategoryProjectionRepository categories;

    public GoalService(GoalRepository goals, GoalEvaluationRepository evaluations,
            ExpenseProjectionRepository expenses, CategoryProjectionRepository categories) {
        this.goals = goals;
        this.evaluations = evaluations;
        this.expenses = expenses;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<GoalStatus> list(UUID userId, LocalDate today) {
        return goals.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(goal -> statusOf(goal, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalStatus get(UUID id, UUID userId, LocalDate today) {
        return statusOf(require(id, userId), today);
    }

    @Transactional
    public GoalEntity create(UUID userId, String name, UUID categoryId, long targetAmount,
            String period, LocalDate startDate, LocalDate endDate) {
        return goals.save(new GoalEntity(userId, name, categoryId, targetAmount, period, startDate, endDate));
    }

    @Transactional
    public GoalEntity update(UUID id, UUID userId, String name, UUID categoryId, long targetAmount,
            String period, LocalDate startDate, LocalDate endDate, boolean active) {
        GoalEntity goal = require(id, userId);
        goal.update(name, categoryId, targetAmount, period, startDate, endDate, active);
        return goals.save(goal);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        goals.delete(require(id, userId));
    }

    GoalEntity require(UUID id, UUID userId) {
        return goals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found"));
    }

    /** Current-period actual for the goal (period containing today). */
    private GoalStatus statusOf(GoalEntity goal, LocalDate today) {
        LocalDate start = periodStart(goal.getPeriod(), today);
        LocalDate end = periodEnd(goal.getPeriod(), today);
        long actual = actualFor(goal, start, end);
        return new GoalStatus(goal, actual, actual <= goal.getTargetAmount(), start, end);
    }

    /** Sums projected expenses for the goal's scope within [from, to]. */
    long actualFor(GoalEntity goal, LocalDate from, LocalDate to) {
        List<ExpenseProjectionEntity> rows =
                expenses.findByUserIdAndExpenseDateBetween(goal.getUserId(), from, to);
        Set<UUID> scope = categoryScope(goal);
        return rows.stream()
                .filter(row -> scope == null || scope.contains(row.getCategoryId()))
                .mapToLong(ExpenseProjectionEntity::getAmount)
                .sum();
    }

    /** Bulk variant used by the calendar: aggregate per day for one month. */
    Map<LocalDate, Long> dailyActuals(GoalEntity goal, LocalDate from, LocalDate to) {
        List<ExpenseProjectionEntity> rows =
                expenses.findByUserIdAndExpenseDateBetween(goal.getUserId(), from, to);
        Set<UUID> scope = categoryScope(goal);
        return rows.stream()
                .filter(row -> scope == null || scope.contains(row.getCategoryId()))
                .collect(Collectors.groupingBy(ExpenseProjectionEntity::getExpenseDate,
                        Collectors.summingLong(ExpenseProjectionEntity::getAmount)));
    }

    /** null = all categories; otherwise the goal's category plus its children. */
    private Set<UUID> categoryScope(GoalEntity goal) {
        if (goal.getCategoryId() == null) {
            return null;
        }
        return Stream.concat(
                Stream.of(goal.getCategoryId()),
                categories.findByParentId(goal.getCategoryId()).stream()
                        .map(CategoryProjectionEntity::getCategoryId))
                .collect(Collectors.toSet());
    }

    /** Upserts the persisted evaluation for a completed period. */
    @Transactional
    public void recordEvaluation(GoalEntity goal, LocalDate periodStart, LocalDate periodEnd,
            long actual, boolean met) {
        evaluations.findByGoalIdAndPeriodStart(goal.getId(), periodStart)
                .ifPresentOrElse(
                        existing -> {
                            existing.refresh(actual, met);
                            evaluations.save(existing);
                        },
                        () -> evaluations.save(
                                new GoalEvaluationEntity(goal.getId(), periodStart, periodEnd, actual, met)));
    }

    static LocalDate periodStart(String period, LocalDate date) {
        return switch (period) {
            case "DAILY" -> date;
            case "MONTHLY" -> YearMonth.from(date).atDay(1);
            case "YEARLY" -> date.withDayOfYear(1);
            default -> throw new IllegalArgumentException("Unknown period " + period);
        };
    }

    static LocalDate periodEnd(String period, LocalDate date) {
        return switch (period) {
            case "DAILY" -> date;
            case "MONTHLY" -> YearMonth.from(date).atEndOfMonth();
            case "YEARLY" -> date.withDayOfYear(date.lengthOfYear());
            default -> throw new IllegalArgumentException("Unknown period " + period);
        };
    }
}
