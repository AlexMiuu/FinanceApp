package com.personalfinance.quest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.personalfinance.quest.dto.QuestDataExportDto;
import com.personalfinance.quest.entity.CategoryProjectionEntity;
import com.personalfinance.quest.entity.ExpenseProjectionEntity;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.entity.GoalEvaluationEntity;
import com.personalfinance.quest.entity.QuestEntity;
import com.personalfinance.quest.entity.UserIncomeEntity;
import com.personalfinance.quest.events.Events;
import com.personalfinance.quest.entity.OathEntity;
import com.personalfinance.quest.mapper.GoalMapper;
import com.personalfinance.quest.mapper.OathMapper;
import com.personalfinance.quest.mapper.ProjectionExportMapper;
import com.personalfinance.quest.mapper.QuestMapper;
import com.personalfinance.quest.repository.CategoryProjectionRepository;
import com.personalfinance.quest.repository.ExpenseProjectionRepository;
import com.personalfinance.quest.repository.GoalEvaluationRepository;
import com.personalfinance.quest.repository.GoalRepository;
import com.personalfinance.quest.repository.OathRepository;
import com.personalfinance.quest.repository.QuestRepository;
import com.personalfinance.quest.repository.UserIncomeRepository;

class PrivacyServiceTest {

    private final UUID userId = UUID.randomUUID();

    private GoalRepository goals;
    private GoalEvaluationRepository goalEvaluations;
    private QuestRepository quests;
    private OathRepository oaths;
    private UserIncomeRepository incomes;
    private ExpenseProjectionRepository expenseProjections;
    private CategoryProjectionRepository categoryProjections;
    private List<Object> published;
    private PrivacyService service;

    @BeforeEach
    void setUp() {
        goals = mock(GoalRepository.class);
        goalEvaluations = mock(GoalEvaluationRepository.class);
        quests = mock(QuestRepository.class);
        oaths = mock(OathRepository.class);
        incomes = mock(UserIncomeRepository.class);
        expenseProjections = mock(ExpenseProjectionRepository.class);
        categoryProjections = mock(CategoryProjectionRepository.class);
        published = new ArrayList<>();

        service = new PrivacyService(goals, goalEvaluations, quests, oaths, incomes, expenseProjections,
                categoryProjections, new GoalMapper(), new QuestMapper(), new OathMapper(),
                new ProjectionExportMapper(), published::add);

        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(quests.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(oaths.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
        when(incomes.findById(userId)).thenReturn(Optional.empty());
        when(expenseProjections.findByUserIdOrderByExpenseDateDesc(userId)).thenReturn(List.of());
        when(categoryProjections.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of());
    }

    private GoalEntity goal() {
        return new GoalEntity(userId, "Food budget", UUID.randomUUID(), 50000, "MONTHLY",
                LocalDate.of(2026, 1, 1), null);
    }

    // ---- eraseUserData ----

    @Test
    void eraseUserDataClearsEveryTableThatHoldsTheUser() {
        UUID erasureRequestId = UUID.randomUUID();
        GoalEntity goal = goal();
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(goal));

        service.eraseUserData(erasureRequestId, userId);

        verify(goalEvaluations).deleteByGoalIdIn(List.of(goal.getId()));
        verify(goals).deleteByUserId(userId);
        verify(quests).deleteByUserId(userId);
        verify(oaths).deleteByUserId(userId);
        verify(incomes).deleteByUserId(userId);
        verify(expenseProjections).deleteByUserId(userId);
        verify(categoryProjections).deleteByUserId(userId);
    }

    @Test
    void eraseUserDataPublishesCompletionNamingThisService() {
        UUID erasureRequestId = UUID.randomUUID();

        service.eraseUserData(erasureRequestId, userId);

        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ErasureCompleted.class, event -> {
            assertThat(event.erasureRequestId()).isEqualTo(erasureRequestId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.service()).isEqualTo("quest");
            assertThat(event.routingKey()).isEqualTo("user.erasure.completed");
        });
    }

    @Test
    void evaluationsAreDeletedBeforeTheGoalsTheyHangOff() {
        // Given a user with one goal
        GoalEntity goal = goal();
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(goal));

        // When the erasure runs
        service.eraseUserData(UUID.randomUUID(), userId);

        // Then children go first — goal_evaluations.goal_id is a real FK
        InOrder order = inOrder(goalEvaluations, goals);
        order.verify(goalEvaluations).deleteByGoalIdIn(List.of(goal.getId()));
        order.verify(goals).deleteByUserId(userId);
    }

    @Test
    void eraseUserDataSkipsTheEvaluationSweepForAUserWithNoGoals() {
        // Given a user who never created a goal — an empty IN () list is not a valid query
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        service.eraseUserData(UUID.randomUUID(), userId);

        verify(goalEvaluations, never()).deleteByGoalIdIn(anyList());
        verify(goals).deleteByUserId(userId);
    }

    @Test
    void eraseUserDataForAUserWithNoDataStillAcknowledges() {
        // Given every table is already empty for this user
        UUID erasureRequestId = UUID.randomUUID();

        // When the erasure runs
        service.eraseUserData(erasureRequestId, userId);

        // Then the ack is still published — silence would strand the request in PENDING forever
        assertThat(published).singleElement().isInstanceOf(Events.ErasureCompleted.class);
    }

    @Test
    void aFailedDeleteAbortsBeforePublishingACompletionItCannotHonour() {
        // Given the quests delete blows up mid-erasure
        doThrow(new IllegalStateException("db down")).when(quests).deleteByUserId(userId);

        // When the erasure runs
        assertThatThrownBy(() -> service.eraseUserData(UUID.randomUUID(), userId))
                .isInstanceOf(IllegalStateException.class);

        // Then no completion is announced — the request must stay honestly PENDING
        assertThat(published).isEmpty();
    }

    // ---- exportUserData ----

    @Test
    void exportUserDataAggregatesEveryDataClass() {
        GoalEntity goal = goal();
        GoalEvaluationEntity evaluation = new GoalEvaluationEntity(goal.getId(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 12345, true);
        QuestEntity quest = new QuestEntity(userId, "WEEKLY_CAP", "Keep it under 100 RON",
                Map.of("cap", 10000L), LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12));
        CategoryProjectionEntity category = new CategoryProjectionEntity(
                UUID.randomUUID(), userId, "Housing", null, true);
        ExpenseProjectionEntity expense = new ExpenseProjectionEntity(
                UUID.randomUUID(), userId, category.getCategoryId(), "Housing", true, 5000, "RON",
                "rent", LocalDate.of(2026, 7, 1));

        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(goal));
        when(goalEvaluations.findByGoalIdInOrderByPeriodStartAsc(List.of(goal.getId())))
                .thenReturn(List.of(evaluation));
        when(quests.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(quest));
        when(oaths.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                new OathEntity(userId, category.getCategoryId(), "Housing", 4500,
                        Instant.parse("2026-07-01T10:00:00Z"), Instant.parse("2026-07-01T18:00:00Z"))));
        when(incomes.findById(userId)).thenReturn(Optional.of(new UserIncomeEntity(userId, 600000)));
        when(categoryProjections.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(category));
        when(expenseProjections.findByUserIdOrderByExpenseDateDesc(userId)).thenReturn(List.of(expense));

        QuestDataExportDto export = service.exportUserData(userId);

        assertThat(export.goals()).singleElement()
                .satisfies(dto -> assertThat(dto.id()).isEqualTo(goal.getId()));
        assertThat(export.goalEvaluations()).singleElement()
                .satisfies(dto -> assertThat(dto.actualAmount()).isEqualTo(12345));
        assertThat(export.quests()).singleElement()
                .satisfies(dto -> assertThat(dto.templateCode()).isEqualTo("WEEKLY_CAP"));
        assertThat(export.oaths()).singleElement()
                .satisfies(dto -> assertThat(dto.pledgedAmount()).isEqualTo(4500));
        assertThat(export.categoryProjections()).singleElement()
                .satisfies(dto -> assertThat(dto.categoryId()).isEqualTo(category.getCategoryId()));
        assertThat(export.expenseProjections()).singleElement()
                .satisfies(dto -> assertThat(dto.expenseId()).isEqualTo(expense.getExpenseId()));
        assertThat(export.monthlyIncome()).isNotNull()
                .satisfies(dto -> assertThat(dto.monthlyIncome()).isEqualTo(600000));
    }

    @Test
    void exportUserDataReturnsEmptyCollectionsAndNullIncomeForAUserWithNoData() {
        QuestDataExportDto export = service.exportUserData(userId);

        assertThat(export.goals()).isEmpty();
        assertThat(export.goalEvaluations()).isEmpty();
        assertThat(export.quests()).isEmpty();
        assertThat(export.oaths()).isEmpty();
        assertThat(export.categoryProjections()).isEmpty();
        assertThat(export.expenseProjections()).isEmpty();
        assertThat(export.monthlyIncome()).isNull();
    }

    @Test
    void exportUserDataSkipsTheEvaluationLookupWhenThereAreNoGoals() {
        // Given no goals, so there is no goal id to look evaluations up by
        service.exportUserData(userId);

        verify(goalEvaluations, never()).findByGoalIdInOrderByPeriodStartAsc(anyList());
    }

    @Test
    void exportUserDataExposesNoEntityToTheCaller() {
        // Given a fully populated database for the user
        GoalEntity goal = goal();
        when(goals.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(goal));
        when(goalEvaluations.findByGoalIdInOrderByPeriodStartAsc(any()))
                .thenReturn(List.of(new GoalEvaluationEntity(goal.getId(),
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 1, true)));
        when(quests.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                new QuestEntity(userId, "WEEKLY_CAP", "t", Map.of("cap", 1L),
                        LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))));

        QuestDataExportDto export = service.exportUserData(userId);

        // Then every element is a DTO record — no JPA entity leaks into a response body
        assertThat(export.goals()).allSatisfy(dto -> assertThat(dto).isInstanceOf(Record.class));
        assertThat(export.goalEvaluations()).allSatisfy(dto -> assertThat(dto).isInstanceOf(Record.class));
        assertThat(export.quests()).allSatisfy(dto -> assertThat(dto).isInstanceOf(Record.class));
    }
}
