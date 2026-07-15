package com.personalfinance.quest.goal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the goal-fulfillment calendar (FR-7): per-day status for daily goals
 * plus period summaries for monthly/yearly goals. Completed periods get their
 * evaluation rows upserted as a side effect, building history for M6.
 */
@Service
public class CalendarService {

    public enum DayStatus { MET, MISSED, IN_PROGRESS, FUTURE, NO_GOAL }

    public record GoalOutcome(UUID goalId, String name, long target, long actual, boolean met) {
    }

    public record Day(LocalDate date, DayStatus status, long totalSpent, List<GoalOutcome> goals) {
    }

    public record PeriodSummary(UUID goalId, String name, long target, long actual, boolean met,
            boolean inProgress) {
    }

    public record Calendar(String month, List<Day> days, List<PeriodSummary> monthlyGoals,
            List<PeriodSummary> yearlyGoals) {
    }

    private final GoalRepository goals;
    private final GoalService goalService;

    public CalendarService(GoalRepository goals, GoalService goalService) {
        this.goals = goals;
        this.goalService = goalService;
    }

    @Transactional
    public Calendar build(UUID userId, YearMonth month, LocalDate today) {
        List<GoalEntity> all = goals.findByUserIdOrderByCreatedAtAsc(userId);
        List<GoalEntity> daily = all.stream().filter(g -> "DAILY".equals(g.getPeriod())).toList();
        List<GoalEntity> monthly = all.stream().filter(g -> "MONTHLY".equals(g.getPeriod())).toList();
        List<GoalEntity> yearly = all.stream().filter(g -> "YEARLY".equals(g.getPeriod())).toList();

        // One projection pass per daily goal for the whole month.
        Map<GoalEntity, Map<LocalDate, Long>> dailyActuals = daily.stream()
                .collect(java.util.stream.Collectors.toMap(
                        g -> g,
                        g -> goalService.dailyActuals(g, month.atDay(1), month.atEndOfMonth())));

        List<Day> days = new ArrayList<>();
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            final LocalDate d = date;
            List<GoalOutcome> outcomes = daily.stream()
                    .filter(g -> g.appliesOn(d))
                    .map(g -> {
                        long actual = dailyActuals.get(g).getOrDefault(d, 0L);
                        boolean met = actual <= g.getTargetAmount();
                        if (d.isBefore(today)) {
                            goalService.recordEvaluation(g, d, d, actual, met);
                        }
                        return new GoalOutcome(g.getId(), g.getName(), g.getTargetAmount(), actual, met);
                    })
                    .toList();

            long totalSpent = outcomes.isEmpty() ? 0
                    : outcomes.stream().mapToLong(GoalOutcome::actual).max().orElse(0);

            DayStatus status;
            if (outcomes.isEmpty()) {
                status = DayStatus.NO_GOAL;
            } else if (d.isAfter(today)) {
                status = DayStatus.FUTURE;
            } else if (d.isEqual(today)) {
                status = DayStatus.IN_PROGRESS;
            } else {
                status = outcomes.stream().allMatch(GoalOutcome::met) ? DayStatus.MET : DayStatus.MISSED;
            }
            days.add(new Day(d, status, totalSpent, outcomes));
        }

        List<PeriodSummary> monthlySummaries = monthly.stream()
                .filter(g -> g.appliesOn(month.atEndOfMonth()) || g.appliesOn(month.atDay(1)))
                .map(g -> summarize(g, month.atDay(1), month.atEndOfMonth(), today))
                .toList();

        LocalDate yearStart = month.atDay(1).withDayOfYear(1);
        LocalDate yearEnd = yearStart.withDayOfYear(yearStart.lengthOfYear());
        List<PeriodSummary> yearlySummaries = yearly.stream()
                .filter(g -> g.appliesOn(yearEnd) || g.appliesOn(yearStart))
                .map(g -> summarize(g, yearStart, yearEnd, today))
                .toList();

        return new Calendar(month.toString(), days, monthlySummaries, yearlySummaries);
    }

    private PeriodSummary summarize(GoalEntity goal, LocalDate start, LocalDate end, LocalDate today) {
        long actual = goalService.actualFor(goal, start, end);
        boolean met = actual <= goal.getTargetAmount();
        boolean inProgress = !end.isBefore(today);
        if (!inProgress) {
            goalService.recordEvaluation(goal, start, end, actual, met);
        }
        return new PeriodSummary(goal.getId(), goal.getName(), goal.getTargetAmount(), actual, met, inProgress);
    }
}
