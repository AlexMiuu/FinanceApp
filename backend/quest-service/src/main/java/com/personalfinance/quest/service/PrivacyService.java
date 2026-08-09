package com.personalfinance.quest.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.quest.dto.QuestDataExportDto;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.events.Events;
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

/**
 * GDPR erasure (Art. 17) and export (Art. 20) for everything quests_db holds
 * about one user, per docs/erasure-event-contract.md §4.
 */
@Service
public class PrivacyService {

    private static final String SERVICE_NAME = "quest";

    private final GoalRepository goals;
    private final GoalEvaluationRepository goalEvaluations;
    private final QuestRepository quests;
    private final OathRepository oaths;
    private final UserIncomeRepository incomes;
    private final ExpenseProjectionRepository expenseProjections;
    private final CategoryProjectionRepository categoryProjections;
    private final GoalMapper goalMapper;
    private final QuestMapper questMapper;
    private final OathMapper oathMapper;
    private final ProjectionExportMapper projectionExportMapper;
    private final ApplicationEventPublisher events;

    public PrivacyService(GoalRepository goals, GoalEvaluationRepository goalEvaluations,
            QuestRepository quests, OathRepository oaths, UserIncomeRepository incomes,
            ExpenseProjectionRepository expenseProjections, CategoryProjectionRepository categoryProjections,
            GoalMapper goalMapper, QuestMapper questMapper, OathMapper oathMapper,
            ProjectionExportMapper projectionExportMapper, ApplicationEventPublisher events) {
        this.goals = goals;
        this.goalEvaluations = goalEvaluations;
        this.quests = quests;
        this.oaths = oaths;
        this.incomes = incomes;
        this.expenseProjections = expenseProjections;
        this.categoryProjections = categoryProjections;
        this.goalMapper = goalMapper;
        this.questMapper = questMapper;
        this.oathMapper = oathMapper;
        this.projectionExportMapper = projectionExportMapper;
        this.events = events;
    }

    /**
     * Evaluations are deleted explicitly before their goals rather than left to
     * the goal_evaluations -&gt; goals ON DELETE CASCADE: the cascade would still
     * clear them, but relying on it would make the erasure silently dependent on
     * a schema detail that no test in this service can see. Everything else here
     * is keyed directly by user_id.
     */
    @Transactional
    public void eraseUserData(UUID erasureRequestId, UUID userId) {
        List<UUID> goalIds = goals.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(GoalEntity::getId)
                .toList();
        if (!goalIds.isEmpty()) {
            goalEvaluations.deleteByGoalIdIn(goalIds);
        }
        goals.deleteByUserId(userId);
        quests.deleteByUserId(userId);
        oaths.deleteByUserId(userId);
        incomes.deleteByUserId(userId);
        expenseProjections.deleteByUserId(userId);
        categoryProjections.deleteByUserId(userId);
        events.publishEvent(new Events.ErasureCompleted(erasureRequestId, userId, SERVICE_NAME, Instant.now()));
    }

    @Transactional(readOnly = true)
    public QuestDataExportDto exportUserData(UUID userId) {
        List<GoalEntity> userGoals = goals.findByUserIdOrderByCreatedAtAsc(userId);
        List<UUID> goalIds = userGoals.stream().map(GoalEntity::getId).toList();

        return new QuestDataExportDto(
                projectionExportMapper.toCategoryDtos(categoryProjections.findByUserIdOrderByNameAsc(userId)),
                projectionExportMapper.toExpenseDtos(
                        expenseProjections.findByUserIdOrderByExpenseDateDesc(userId)),
                goalMapper.toExportDtos(userGoals),
                goalIds.isEmpty() ? List.of()
                        : goalMapper.toEvaluationDtos(
                                goalEvaluations.findByGoalIdInOrderByPeriodStartAsc(goalIds)),
                questMapper.toExportDtos(quests.findByUserIdOrderByCreatedAtDesc(userId)),
                oathMapper.toExportDtos(oaths.findByUserIdOrderByCreatedAtAsc(userId)),
                incomes.findById(userId).map(questMapper::toIncomeDto).orElse(null));
    }
}
