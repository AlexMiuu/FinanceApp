package com.personalfinance.quest.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.quest.dto.CalendarDayDto;
import com.personalfinance.quest.dto.CalendarDto;
import com.personalfinance.quest.dto.DayStatusDto;
import com.personalfinance.quest.dto.GoalOutcomeDto;
import com.personalfinance.quest.dto.PeriodSummaryDto;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.repository.GoalRepository;

/**
 * Builds the goal-fulfillment calendar (FR-7): per-day status for daily goals
 * plus period summaries for monthly/yearly goals. Completed periods get their
 * evaluation rows upserted as a side effect, building history for M6.
 *
 * <p>The calendar is a computed aggregate with no entity of its own, so it is
 * assembled straight into its DTO here rather than through a mapper — the same
 * shape report-service's DashboardService uses.
 */
@Service
public class CalendarService {

    private final GoalRepository goals;
    private final GoalService goalService;

    public CalendarService(GoalRepository goals, GoalService goalService) {
        this.goals = goals;
        this.goalService = goalService;
    }

    @Transactional
    public CalendarDto build(UUID userId, YearMonth month, LocalDate today) {
        List<GoalEntity> all = goals.findByUserIdOrderByCreatedAtAsc(userId);
        List<GoalEntity> daily = all.stream().filter(g -> "DAILY".equals(g.getPeriod())).toList();
        List<GoalEntity> monthly = all.stream().filter(g -> "MONTHLY".equals(g.getPeriod())).toList();
        List<GoalEntity> yearly = all.stream().filter(g -> "YEARLY".equals(g.getPeriod())).toList();

        // One projection pass per daily goal for the whole month.
        Map<GoalEntity, Map<LocalDate, Long>> dailyActuals = daily.stream()
                .collect(Collectors.toMap(
                        g -> g,
                        g -> goalService.dailyActuals(g, month.atDay(1), month.atEndOfMonth())));

        List<CalendarDayDto> days = new ArrayList<>();
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            final LocalDate d = date;
            List<GoalOutcomeDto> outcomes = daily.stream()
                    .filter(g -> g.appliesOn(d))
                    .map(g -> {
                        long actual = dailyActuals.get(g).getOrDefault(d, 0L);
                        boolean met = actual <= g.getTargetAmount();
                        if (d.isBefore(today)) {
                            goalService.recordEvaluation(g, d, d, actual, met);
                        }
                        return new GoalOutcomeDto(g.getId(), g.getName(), g.getTargetAmount(), actual, met);
                    })
                    .toList();

            long totalSpent = outcomes.isEmpty() ? 0
                    : outcomes.stream().mapToLong(GoalOutcomeDto::actual).max().orElse(0);

            days.add(new CalendarDayDto(d, dayStatus(outcomes, d, today), totalSpent, outcomes));
        }

        List<PeriodSummaryDto> monthlySummaries = monthly.stream()
                .filter(g -> g.appliesOn(month.atEndOfMonth()) || g.appliesOn(month.atDay(1)))
                .map(g -> summarize(g, month.atDay(1), month.atEndOfMonth(), today))
                .toList();

        LocalDate yearStart = month.atDay(1).withDayOfYear(1);
        LocalDate yearEnd = yearStart.withDayOfYear(yearStart.lengthOfYear());
        List<PeriodSummaryDto> yearlySummaries = yearly.stream()
                .filter(g -> g.appliesOn(yearEnd) || g.appliesOn(yearStart))
                .map(g -> summarize(g, yearStart, yearEnd, today))
                .toList();

        return new CalendarDto(month.toString(), days, monthlySummaries, yearlySummaries);
    }

    static DayStatusDto dayStatus(List<GoalOutcomeDto> outcomes, LocalDate day, LocalDate today) {
        if (outcomes.isEmpty()) {
            return DayStatusDto.NO_GOAL;
        }
        if (day.isAfter(today)) {
            return DayStatusDto.FUTURE;
        }
        if (day.isEqual(today)) {
            return DayStatusDto.IN_PROGRESS;
        }
        return outcomes.stream().allMatch(GoalOutcomeDto::met) ? DayStatusDto.MET : DayStatusDto.MISSED;
    }

    private PeriodSummaryDto summarize(GoalEntity goal, LocalDate start, LocalDate end, LocalDate today) {
        long actual = goalService.actualFor(goal, start, end);
        boolean met = actual <= goal.getTargetAmount();
        boolean inProgress = !end.isBefore(today);
        if (!inProgress) {
            goalService.recordEvaluation(goal, start, end, actual, met);
        }
        return new PeriodSummaryDto(goal.getId(), goal.getName(), goal.getTargetAmount(), actual, met, inProgress);
    }
}
