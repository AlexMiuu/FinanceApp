package com.personalfinance.quest.quest;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.quest.domain.ExpenseProjectionEntity;
import com.personalfinance.quest.domain.ExpenseProjectionRepository;
import com.personalfinance.quest.events.EventsConfig;
import com.personalfinance.quest.web.ApiExceptions.NotFoundException;

/**
 * Tailored quests (O3/FR-11). Generation looks at the user's history, income,
 * and mandatory-expense split; progress is re-evaluated from the projection on
 * reads and on incoming expense events.
 */
@Service
public class QuestService {

    public record QuestView(QuestEntity quest, long target, long progress, String kind) {
    }

    public record QuestEvent(UUID questId, UUID userId, String title, String status, Instant occurredAt) {
    }

    private static final Logger log = LoggerFactory.getLogger(QuestService.class);
    private static final long MIN_CAP = 10_00; // 10 RON floor so caps never degenerate

    private final QuestRepository quests;
    private final ExpenseProjectionRepository expenses;
    private final UserIncomeRepository incomes;
    private final RabbitTemplate rabbitTemplate;

    public QuestService(QuestRepository quests, ExpenseProjectionRepository expenses,
            UserIncomeRepository incomes, RabbitTemplate rabbitTemplate) {
        this.quests = quests;
        this.expenses = expenses;
        this.incomes = incomes;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ---- generation ----

    /**
     * Idempotent: one quest per template per period (DB unique constraint).
     * Caps are derived from PREVIOUS full weeks (ending last Sunday) and a cap
     * quest is never suggested when the current period's spending already
     * exceeds it — a quest that is failed on arrival helps nobody.
     */
    @Transactional
    public void generateSuggestions(UUID userId, LocalDate today) {
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate prevWeekStart = weekStart.minusDays(7);
        LocalDate prevWeekEnd = weekStart.minusDays(1);
        YearMonth month = YearMonth.from(today);

        // Baseline: the 4 full weeks before this one. Current: this week so far.
        List<ExpenseProjectionEntity> baseline =
                expenses.findByUserIdAndExpenseDateBetween(userId, weekStart.minusDays(28), prevWeekEnd);
        List<ExpenseProjectionEntity> currentWeek =
                expenses.findByUserIdAndExpenseDateBetween(userId, weekStart, today);
        boolean hasHistory = !baseline.isEmpty();

        // CATEGORY_CAP: top discretionary top-level category of the previous week.
        if (!quests.existsByUserIdAndTemplateCodeAndPeriodStart(userId, "CATEGORY_CAP", weekStart)) {
            baseline.stream()
                    .filter(row -> !row.isMandatory())
                    .filter(row -> !row.getExpenseDate().isBefore(prevWeekStart))
                    .collect(Collectors.groupingBy(row -> row.getCategoryPath().split(" > ")[0],
                            Collectors.summingLong(ExpenseProjectionEntity::getAmount)))
                    .entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .filter(top -> top.getValue() >= MIN_CAP)
                    .ifPresent(top -> {
                        long cap = Math.max(MIN_CAP, Math.round(top.getValue() * 0.85));
                        long alreadySpent = currentWeek.stream()
                                .filter(row -> row.getCategoryPath().split(" > ")[0].equals(top.getKey()))
                                .mapToLong(ExpenseProjectionEntity::getAmount).sum();
                        if (alreadySpent <= cap) {
                            suggest(userId, "CATEGORY_CAP",
                                    "Spend under " + ron(cap) + " on " + top.getKey() + " this week",
                                    Map.of("cap", cap, "categoryName", top.getKey()),
                                    weekStart, weekEnd);
                        }
                    });
        }

        // WEEKLY_CAP: 85% of the previous-4-weeks discretionary average;
        // income-based fallback when there is no history yet.
        if (!quests.existsByUserIdAndTemplateCodeAndPeriodStart(userId, "WEEKLY_CAP", weekStart)) {
            long discretionary28 = baseline.stream()
                    .filter(row -> !row.isMandatory())
                    .mapToLong(ExpenseProjectionEntity::getAmount).sum();
            Long cap = null;
            if (hasHistory && discretionary28 > 0) {
                cap = Math.max(MIN_CAP, Math.round(discretionary28 / 4.0 * 0.85));
            } else {
                long monthlyIncome = incomes.findById(userId)
                        .map(UserIncomeEntity::getMonthlyIncome).orElse(0L);
                if (monthlyIncome > 0) {
                    cap = Math.max(MIN_CAP, Math.round(monthlyIncome * 0.25 / 4.33));
                }
            }
            if (cap != null) {
                long alreadySpent = currentWeek.stream()
                        .filter(row -> !row.isMandatory())
                        .mapToLong(ExpenseProjectionEntity::getAmount).sum();
                if (alreadySpent <= cap) {
                    suggest(userId, "WEEKLY_CAP",
                            "Keep discretionary spending under " + ron(cap) + " this week",
                            Map.of("cap", cap), weekStart, weekEnd);
                }
            }
        }

        // NO_SPEND_DAYS: needs at least a week of history to be meaningful.
        if (hasHistory
                && !quests.existsByUserIdAndTemplateCodeAndPeriodStart(userId, "NO_SPEND_DAYS", weekStart)) {
            suggest(userId, "NO_SPEND_DAYS", "Have 2 no-spend days this week",
                    Map.of("days", 2), weekStart, weekEnd);
        }

        // BEAT_LAST_MONTH: only when there is a previous month to beat.
        if (!quests.existsByUserIdAndTemplateCodeAndPeriodStart(userId, "BEAT_LAST_MONTH", month.atDay(1))) {
            YearMonth previous = month.minusMonths(1);
            long lastMonthTotal = expenses.sumForRange(userId, previous.atDay(1), previous.atEndOfMonth());
            if (lastMonthTotal >= MIN_CAP) {
                suggest(userId, "BEAT_LAST_MONTH",
                        "Spend less than " + ron(lastMonthTotal) + " this month (beat " + previous + ")",
                        Map.of("cap", lastMonthTotal), month.atDay(1), month.atEndOfMonth());
            }
        }
    }

    private void suggest(UUID userId, String code, String title, Map<String, Object> params,
            LocalDate start, LocalDate end) {
        QuestEntity quest = quests.save(new QuestEntity(userId, code, title, new HashMap<>(params), start, end));
        publish(quest, "quest.suggested");
    }

    // ---- lifecycle ----

    @Transactional
    public QuestEntity accept(UUID id, UUID userId) {
        QuestEntity quest = require(id, userId);
        if ("SUGGESTED".equals(quest.getStatus())) {
            quest.setStatus("ACTIVE");
        }
        return quest;
    }

    @Transactional
    public QuestEntity decline(UUID id, UUID userId) {
        QuestEntity quest = require(id, userId);
        if ("SUGGESTED".equals(quest.getStatus())) {
            quest.setStatus("DECLINED");
        }
        return quest;
    }

    private QuestEntity require(UUID id, UUID userId) {
        return quests.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Quest not found"));
    }

    // ---- progress ----

    @Transactional
    public List<QuestView> list(UUID userId, LocalDate today) {
        generateSuggestions(userId, today);
        return quests.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(quest -> evaluate(quest, today))
                .toList();
    }

    /** Called from the expense event listener for immediate fail/complete. */
    @Transactional
    public void refreshActiveQuests(UUID userId, LocalDate today) {
        quests.findByUserIdAndStatus(userId, "ACTIVE").forEach(quest -> evaluate(quest, today));
    }

    /** Nightly + on-read finalization of quests whose period is over. */
    @Transactional
    public void finalizeExpired(LocalDate today) {
        quests.findByStatusAndPeriodEndBefore("ACTIVE", today)
                .forEach(quest -> evaluate(quest, today));
        quests.findByStatusAndPeriodEndBefore("SUGGESTED", today)
                .forEach(quest -> quest.setStatus("DECLINED"));
    }

    /**
     * Recomputes progress and applies transitions:
     * cap quests fail the moment the cap is exceeded and complete when the
     * period ends under it; no-spend quests complete as soon as enough zero-
     * spend days have passed and fail when the period ends short.
     */
    QuestView evaluate(QuestEntity quest, LocalDate today) {
        boolean noSpend = "NO_SPEND_DAYS".equals(quest.getTemplateCode());
        long target = noSpend ? quest.paramLong("days") : quest.paramLong("cap");
        long progress = noSpend ? countNoSpendDays(quest, today) : spentInScope(quest, today);

        if ("ACTIVE".equals(quest.getStatus())) {
            boolean periodOver = quest.getPeriodEnd().isBefore(today);
            if (noSpend) {
                if (progress >= target) {
                    transition(quest, "COMPLETED");
                } else if (periodOver) {
                    transition(quest, "FAILED");
                }
            } else {
                if (progress > target) {
                    transition(quest, "FAILED");
                } else if (periodOver) {
                    transition(quest, "COMPLETED");
                }
            }
        }
        quest.setProgressAmount(progress);
        return new QuestView(quest, target, progress, noSpend ? "DAYS" : "CAP");
    }

    private long spentInScope(QuestEntity quest, LocalDate today) {
        String categoryName = quest.getParams() == null ? null
                : (String) quest.getParams().get("categoryName");
        boolean discretionaryOnly = "WEEKLY_CAP".equals(quest.getTemplateCode());
        // Whole period, not clamped to "today": an expense dated anywhere in
        // the period counts against the cap (also avoids UTC-vs-local edges).
        return expenses.findByUserIdAndExpenseDateBetween(
                quest.getUserId(), quest.getPeriodStart(), quest.getPeriodEnd())
                .stream()
                .filter(row -> categoryName == null
                        || row.getCategoryPath().split(" > ")[0].equals(categoryName))
                .filter(row -> !discretionaryOnly || !row.isMandatory())
                .mapToLong(ExpenseProjectionEntity::getAmount)
                .sum();
    }

    /** Fully elapsed days (before today) within the period with zero spending. */
    private long countNoSpendDays(QuestEntity quest, LocalDate today) {
        LocalDate lastElapsed = quest.getPeriodEnd().isBefore(today) ? quest.getPeriodEnd() : today.minusDays(1);
        if (lastElapsed.isBefore(quest.getPeriodStart())) {
            return 0;
        }
        Map<LocalDate, Long> perDay = expenses
                .findByUserIdAndExpenseDateBetween(quest.getUserId(), quest.getPeriodStart(), lastElapsed)
                .stream()
                .collect(Collectors.groupingBy(ExpenseProjectionEntity::getExpenseDate,
                        Collectors.summingLong(ExpenseProjectionEntity::getAmount)));
        return quest.getPeriodStart().datesUntil(lastElapsed.plusDays(1))
                .filter(day -> perDay.getOrDefault(day, 0L) == 0)
                .count();
    }

    private void transition(QuestEntity quest, String status) {
        quest.setStatus(status);
        publish(quest, "quest." + status.toLowerCase());
    }

    private void publish(QuestEntity quest, String routingKey) {
        try {
            rabbitTemplate.convertAndSend(EventsConfig.EXCHANGE, routingKey,
                    new QuestEvent(quest.getId(), quest.getUserId(), quest.getTitle(),
                            quest.getStatus(), Instant.now()));
        } catch (Exception e) {
            log.error("Failed to publish {} for quest {}", routingKey, quest.getId(), e);
        }
    }

    private static String ron(long bani) {
        return (bani / 100) + " RON";
    }
}
