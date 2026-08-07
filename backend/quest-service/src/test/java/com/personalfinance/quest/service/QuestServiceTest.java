package com.personalfinance.quest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.quest.entity.ExpenseProjectionEntity;
import com.personalfinance.quest.entity.QuestEntity;
import com.personalfinance.quest.entity.UserIncomeEntity;
import com.personalfinance.quest.events.Events;
import com.personalfinance.quest.exception.NotFoundException;
import com.personalfinance.quest.repository.ExpenseProjectionRepository;
import com.personalfinance.quest.repository.QuestRepository;
import com.personalfinance.quest.repository.UserIncomeRepository;

class QuestServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.of(2026, 7, 15); // a Wednesday

    private QuestRepository quests;
    private ExpenseProjectionRepository expenses;
    private UserIncomeRepository incomes;
    private QuestService service;
    private final List<QuestEntity> saved = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();

    @BeforeEach
    void setUp() {
        quests = mock(QuestRepository.class);
        expenses = mock(ExpenseProjectionRepository.class);
        incomes = mock(UserIncomeRepository.class);
        service = new QuestService(quests, expenses, incomes, published::add);
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

    private List<Events.QuestChanged> publishedQuestEvents() {
        return published.stream()
                .filter(Events.QuestChanged.class::isInstance)
                .map(Events.QuestChanged.class::cast)
                .toList();
    }

    // ---- generateSuggestions ----

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
        when(incomes.findById(userId)).thenReturn(Optional.of(new UserIncomeEntity(userId, 600000)));

        service.generateSuggestions(userId, today);

        QuestEntity weeklyCap = saved.stream()
                .filter(q -> q.getTemplateCode().equals("WEEKLY_CAP"))
                .findFirst().orElseThrow();
        // 25% of 6000 RON monthly / 4.33 weeks ≈ 346.42 RON
        assertThat(weeklyCap.paramLong("cap")).isEqualTo(Math.round(600000 * 0.25 / 4.33));
    }

    @Test
    void noHistoryAndNoIncomeSuggestsNothingAtAll() {
        // Given a brand-new user: empty projection, no income event yet
        givenExpenses(List.of());
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);
        when(incomes.findById(userId)).thenReturn(Optional.empty());

        // When suggestions are generated
        service.generateSuggestions(userId, today);

        // Then nothing is invented out of thin air
        assertThat(saved).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void aCategorySpendBelowTheTenRonFloorIsNotWorthACapQuest() {
        // Given last week's top discretionary category is 9.99 RON — just under MIN_CAP
        givenExpenses(List.of(row("Snacks", false, 999, LocalDate.of(2026, 7, 8))));
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);

        service.generateSuggestions(userId, today);

        // Then no CATEGORY_CAP is raised, though history exists so NO_SPEND_DAYS still is
        assertThat(saved.stream().map(QuestEntity::getTemplateCode))
                .doesNotContain("CATEGORY_CAP")
                .contains("NO_SPEND_DAYS");
    }

    @Test
    void alreadySuggestedTemplatesAreNotSuggestedTwiceForTheSamePeriod() {
        givenExpenses(List.of(row("Food > Groceries", false, 20000, LocalDate.of(2026, 7, 8))));
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);
        when(quests.existsByUserIdAndTemplateCodeAndPeriodStart(any(), anyString(), any())).thenReturn(true);

        service.generateSuggestions(userId, today);

        assertThat(saved).isEmpty();
    }

    @Test
    void aSuggestionIsPublishedAsQuestSuggested() {
        givenExpenses(List.of(row("Food > Groceries", false, 20000, LocalDate.of(2026, 7, 8))));
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);

        service.generateSuggestions(userId, today);

        assertThat(publishedQuestEvents()).isNotEmpty()
                .allSatisfy(event -> {
                    assertThat(event.routingKey()).isEqualTo("quest.suggested");
                    assertThat(event.userId()).isEqualTo(userId);
                });
    }

    // ---- accept / decline ----

    @Test
    void acceptMovesASuggestedQuestToActive() {
        QuestEntity quest = quest("WEEKLY_CAP", Map.of("cap", 10000L));
        when(quests.findByIdAndUserId(quest.getId(), userId)).thenReturn(Optional.of(quest));

        assertThat(service.accept(quest.getId(), userId).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void acceptingAnAlreadyFailedQuestLeavesItFailed() {
        // Given a quest that has already been resolved
        QuestEntity quest = quest("WEEKLY_CAP", Map.of("cap", 10000L));
        quest.setStatus("FAILED");
        when(quests.findByIdAndUserId(quest.getId(), userId)).thenReturn(Optional.of(quest));

        // When it is accepted anyway
        // Then the terminal status is not resurrected
        assertThat(service.accept(quest.getId(), userId).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void declineMovesASuggestedQuestToDeclined() {
        QuestEntity quest = quest("WEEKLY_CAP", Map.of("cap", 10000L));
        when(quests.findByIdAndUserId(quest.getId(), userId)).thenReturn(Optional.of(quest));

        assertThat(service.decline(quest.getId(), userId).getStatus()).isEqualTo("DECLINED");
    }

    @Test
    void acceptThrowsNotFoundForAQuestBelongingToAnotherUser() {
        UUID id = UUID.randomUUID();
        when(quests.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(id, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Quest not found");
    }

    @Test
    void declineThrowsNotFoundForAQuestBelongingToAnotherUser() {
        UUID id = UUID.randomUUID();
        when(quests.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decline(id, userId)).isInstanceOf(NotFoundException.class);
    }

    // ---- evaluate ----

    @Test
    void capQuestFailsImmediatelyWhenExceeded() {
        QuestEntity quest = quest("WEEKLY_CAP", Map.of("cap", 10000L));
        quest.setStatus("ACTIVE");
        givenExpenses(List.of(row("Food > Groceries", false, 15000, today)));

        QuestView view = service.evaluate(quest, today);

        assertThat(view.quest().getStatus()).isEqualTo("FAILED");
        assertThat(publishedQuestEvents()).singleElement().satisfies(event -> {
            assertThat(event.routingKey()).isEqualTo("quest.failed");
            assertThat(event.questId()).isEqualTo(quest.getId());
        });
    }

    @Test
    void capQuestSurvivesSpendingExactlyAtTheCap() {
        // Given an active cap quest and spending that lands exactly on the cap
        QuestEntity quest = quest("WEEKLY_CAP", Map.of("cap", 10000L));
        quest.setStatus("ACTIVE");
        givenExpenses(List.of(row("Food > Groceries", false, 10000, today)));

        // When it is evaluated mid-period
        QuestView view = service.evaluate(quest, today);

        // Then the failure is strictly "over", not "at" — nothing is published
        assertThat(view.quest().getStatus()).isEqualTo("ACTIVE");
        assertThat(publishedQuestEvents()).isEmpty();
    }

    @Test
    void noSpendQuestCompletesWhenEnoughQuietDays() {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        QuestEntity quest = new QuestEntity(userId, "NO_SPEND_DAYS", "days", Map.of("days", 2L),
                monday, monday.plusDays(6));
        quest.setStatus("ACTIVE");
        // Only spending on Monday; Tuesday elapsed quiet. Wednesday (today) not counted yet.
        givenExpenses(List.of(row("Food > Groceries", false, 1000, monday)));

        QuestView midWeek = service.evaluate(quest, today);
        assertThat(midWeek.progress()).isEqualTo(1);   // only Tuesday counts so far
        assertThat(midWeek.quest().getStatus()).isEqualTo("ACTIVE");

        // By Friday, Tue+Wed+Thu are quiet -> completed
        QuestView friday = service.evaluate(quest, monday.plusDays(4));
        assertThat(friday.progress()).isEqualTo(3);
        assertThat(friday.quest().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void noSpendProgressIsZeroOnTheFirstDayOfThePeriod() {
        // Given a quest evaluated on its own start date — no day has fully elapsed
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        QuestEntity quest = new QuestEntity(userId, "NO_SPEND_DAYS", "days", Map.of("days", 2L),
                monday, monday.plusDays(6));
        quest.setStatus("ACTIVE");
        givenExpenses(List.of());

        // When evaluated on the start date
        QuestView view = service.evaluate(quest, monday);

        // Then progress is zero rather than a negative or off-by-one count
        assertThat(view.progress()).isZero();
        assertThat(view.quest().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void aCategoryPathIsScopedByItsTopLevelSegmentOnly() {
        // Given a CATEGORY_CAP scoped to "Food"
        QuestEntity quest = new QuestEntity(userId, "CATEGORY_CAP", "cap",
                Map.of("cap", 100000L, "categoryName", "Food"),
                today.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY).plusDays(6));
        quest.setStatus("ACTIVE");
        givenExpenses(List.of(
                row("Food > Groceries", false, 3000, today),
                row("Food", false, 2000, today),
                row("Transport > Fuel", false, 9000, today)));

        // When evaluated
        QuestView view = service.evaluate(quest, today);

        // Then children of Food count and other top-level categories do not
        assertThat(view.progress()).isEqualTo(5000);
    }

    @Test
    void aNullCategoryPathIsScopedOutInsteadOfThrowing() {
        // Given a projection row whose denormalized path is missing
        QuestEntity quest = new QuestEntity(userId, "CATEGORY_CAP", "cap",
                Map.of("cap", 100000L, "categoryName", "Food"),
                today.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY).plusDays(6));
        quest.setStatus("ACTIVE");
        givenExpenses(List.of(
                row(null, false, 4000, today),
                row("Food > Groceries", false, 1000, today)));

        // When evaluated
        QuestView view = service.evaluate(quest, today);

        // Then the null-path row is simply out of scope — no NullPointerException
        assertThat(view.progress()).isEqualTo(1000);
    }

    @Test
    void aSuggestedQuestIsNeverTransitionedByEvaluation() {
        // Given a SUGGESTED cap quest already blown well past its cap
        QuestEntity quest = quest("WEEKLY_CAP", Map.of("cap", 100L));
        givenExpenses(List.of(row("Food > Groceries", false, 99999, today)));

        // When it is evaluated
        QuestView view = service.evaluate(quest, today);

        // Then only accepted quests can fail — a suggestion the user never took on does not
        assertThat(view.quest().getStatus()).isEqualTo("SUGGESTED");
        assertThat(publishedQuestEvents()).isEmpty();
    }

    // ---- finalizeExpired ----

    @Test
    void finalizeExpiredCompletesActiveCapQuestsAndDeclinesStaleSuggestions() {
        LocalDate lastWeekMonday = today.with(DayOfWeek.MONDAY).minusDays(7);
        QuestEntity active = new QuestEntity(userId, "WEEKLY_CAP", "cap", Map.of("cap", 100000L),
                lastWeekMonday, lastWeekMonday.plusDays(6));
        active.setStatus("ACTIVE");
        QuestEntity stale = new QuestEntity(userId, "NO_SPEND_DAYS", "days", Map.of("days", 2L),
                lastWeekMonday, lastWeekMonday.plusDays(6));

        when(quests.findByStatusAndPeriodEndBefore("ACTIVE", today)).thenReturn(List.of(active));
        when(quests.findByStatusAndPeriodEndBefore("SUGGESTED", today)).thenReturn(List.of(stale));
        givenExpenses(List.of(row("Food > Groceries", false, 1000, lastWeekMonday)));

        service.finalizeExpired(today);

        assertThat(active.getStatus()).isEqualTo("COMPLETED");
        assertThat(stale.getStatus()).isEqualTo("DECLINED");
        // A declined suggestion is a silent housekeeping step — only the completion is announced
        assertThat(publishedQuestEvents()).singleElement()
                .satisfies(event -> assertThat(event.routingKey()).isEqualTo("quest.completed"));
    }

    @Test
    void finalizeExpiredPublishesNothingWhenNoQuestHasExpired() {
        when(quests.findByStatusAndPeriodEndBefore(anyString(), any())).thenReturn(List.of());

        service.finalizeExpired(today);

        assertThat(published).isEmpty();
    }

    // ---- refreshActiveQuests ----

    @Test
    void refreshActiveQuestsOnlyLooksAtActiveOnes() {
        when(quests.findByUserIdAndStatus(userId, "ACTIVE")).thenReturn(List.of());

        service.refreshActiveQuests(userId, today);

        verify(quests).findByUserIdAndStatus(userId, "ACTIVE");
        verify(quests, never()).findByUserIdAndStatus(userId, "SUGGESTED");
        assertThat(published).isEmpty();
    }

    // ---- list ----

    @Test
    void listGeneratesSuggestionsThenReturnsEveryQuestEvaluated() {
        givenExpenses(List.of());
        when(expenses.sumForRange(eq(userId), any(), any())).thenReturn(0L);
        when(incomes.findById(userId)).thenReturn(Optional.empty());
        QuestEntity existing = quest("WEEKLY_CAP", Map.of("cap", 10000L));
        when(quests.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(existing));

        List<QuestView> views = service.list(userId, today);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.quest()).isSameAs(existing);
            assertThat(view.kind()).isEqualTo("CAP");
            assertThat(view.target()).isEqualTo(10000L);
        });
    }

    private QuestEntity quest(String templateCode, Map<String, Object> params) {
        return new QuestEntity(userId, templateCode, "title", params,
                today.with(DayOfWeek.MONDAY), today.with(DayOfWeek.MONDAY).plusDays(6));
    }
}
