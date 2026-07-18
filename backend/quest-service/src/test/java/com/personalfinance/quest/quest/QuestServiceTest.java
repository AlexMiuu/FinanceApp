package com.personalfinance.quest.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.personalfinance.quest.domain.ExpenseProjectionEntity;
import com.personalfinance.quest.domain.ExpenseProjectionRepository;

class QuestServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 7, 15); // a Wednesday

    private QuestRepository quests;
    private ExpenseProjectionRepository expenses;
    private UserIncomeRepository incomes;
    private RabbitTemplate rabbit;
    private QuestService service;
    private final List<QuestEntity> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        quests = mock(QuestRepository.class);
        expenses = mock(ExpenseProjectionRepository.class);
        incomes = mock(UserIncomeRepository.class);
        rabbit = mock(RabbitTemplate.class);
        service = new QuestService(quests, expenses, incomes, rabbit);
        when(quests.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(quests.existsByUserIdAndTemplateCodeAndPeriodStart(any(), anyString(), any())).thenReturn(false);
    }

    private ExpenseProjectionEntity row(String path, boolean mandatory, long amount, LocalDate date) {
        return new ExpenseProjectionEntity(UUID.randomUUID(), userId, UUID.randomUUID(), path,
                mandatory, amount, "RON", null, date);
    }

    /** Mocks the projection with real date-window filtering (baseline vs current week). */
    private void givenExpenses(List<ExpenseProjectionEntity> rows) {
        when(expenses.findByUserIdAndExpenseDateBetween(eq(userId), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(1);
            LocalDate to = inv.getArgument(2);
            return rows.stream()
                    .filter(r -> !r.getExpenseDate().isBefore(from) && !r.getExpenseDate().isAfter(to))
                    .toList();
        });
    }

    @Test
    void twoWeeksOfHistoryYieldsAtLeastThreeSuggestions() {
        // history entirely in previous weeks: groceries dominates last week
        givenExpenses(List.of(
                row("Food > Groceries", false, 20000, today.minusDays(7)),
                row("Entertainment", false, 5000, today.minusDays(8)),
                row("Housing > Rent", true, 200000, today.minusDays(9)),
                row("Food > Groceries", false, 15000, today.minusDays(20))));
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);

        service.generateSuggestions(userId, today);

        List<String> codes = saved.stream().map(QuestEntity::getTemplateCode).toList();
        assertThat(codes).contains("CATEGORY_CAP", "WEEKLY_CAP", "NO_SPEND_DAYS");
        assertThat(saved.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void categoryCapTargetsTopDiscretionaryCategoryOfPreviousWeek() {
        // prev week = Mon 2026-07-06 .. Sun 2026-07-12
        givenExpenses(List.of(
                row("Food > Groceries", false, 20000, LocalDate.of(2026, 7, 8)),
                row("Housing > Rent", true, 500000, LocalDate.of(2026, 7, 8)),    // mandatory: ignored
                row("Entertainment", false, 8000, LocalDate.of(2026, 6, 10))));   // outside 4-week window
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);

        service.generateSuggestions(userId, today);

        QuestEntity categoryCap = saved.stream()
                .filter(q -> q.getTemplateCode().equals("CATEGORY_CAP"))
                .findFirst().orElseThrow();
        assertThat(categoryCap.getParams().get("categoryName")).isEqualTo("Food");
        assertThat(categoryCap.paramLong("cap")).isEqualTo(17000); // 85% of 200 RON
        assertThat(categoryCap.getPeriodStart()).isEqualTo(today.with(DayOfWeek.MONDAY));
    }

    @Test
    void deadOnArrivalCapQuestsAreNotSuggested() {
        // prev week baseline 100 RON -> cap 85 RON, but this week already 90 RON
        givenExpenses(List.of(
                row("Food > Groceries", false, 10000, LocalDate.of(2026, 7, 8)),
                row("Food > Groceries", false, 9000, LocalDate.of(2026, 7, 14))));
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);

        service.generateSuggestions(userId, today);

        List<String> codes = saved.stream().map(QuestEntity::getTemplateCode).toList();
        assertThat(codes).doesNotContain("CATEGORY_CAP", "WEEKLY_CAP");
        assertThat(codes).contains("NO_SPEND_DAYS");
    }

    @Test
    void weeklyCapFallsBackToIncomeWithoutHistory() {
        givenExpenses(List.of());
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);
        when(incomes.findById(userId)).thenReturn(java.util.Optional.of(new UserIncomeEntity(userId, 600000)));

        service.generateSuggestions(userId, today);

        QuestEntity weeklyCap = saved.stream()
                .filter(q -> q.getTemplateCode().equals("WEEKLY_CAP"))
                .findFirst().orElseThrow();
        // 25% of 6000 RON monthly / 4.33 weeks ≈ 346.42 RON
        assertThat(weeklyCap.paramLong("cap")).isEqualTo(Math.round(600000 * 0.25 / 4.33));
    }

    @Test
    void capQuestFailsImmediatelyWhenExceeded() {
        QuestEntity quest = new QuestEntity(userId, "WEEKLY_CAP", "cap", Map.of("cap", 10000L),
                today.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY).plusDays(6));
        quest.setStatus("ACTIVE");
        givenExpenses(List.of(row("Food > Groceries", false, 15000, today)));

        QuestService.QuestView view = service.evaluate(quest, today);

        assertThat(view.quest().getStatus()).isEqualTo("FAILED");
        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        verify(rabbit).convertAndSend(eq("pf.events"), routingKey.capture(), any(Object.class));
        assertThat(routingKey.getValue()).isEqualTo("quest.failed");
    }

    @Test
    void noSpendQuestCompletesWhenEnoughQuietDays() {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        QuestEntity quest = new QuestEntity(userId, "NO_SPEND_DAYS", "days", Map.of("days", 2L),
                monday, monday.plusDays(6));
        quest.setStatus("ACTIVE");
        // Only spending on Monday; Tuesday elapsed quiet. Wednesday (today) not counted yet.
        givenExpenses(List.of(row("Food > Groceries", false, 1000, monday)));

        QuestService.QuestView midWeek = service.evaluate(quest, today);
        assertThat(midWeek.progress()).isEqualTo(1);   // only Tuesday counts so far
        assertThat(midWeek.quest().getStatus()).isEqualTo("ACTIVE");

        // By Friday, Tue+Wed+Thu are quiet -> completed
        QuestService.QuestView friday = service.evaluate(quest, monday.plusDays(4));
        assertThat(friday.progress()).isEqualTo(3);
        assertThat(friday.quest().getStatus()).isEqualTo("COMPLETED");
    }
}
