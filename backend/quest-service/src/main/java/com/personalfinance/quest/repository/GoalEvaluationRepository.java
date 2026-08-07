package com.personalfinance.quest.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.quest.entity.GoalEvaluationEntity;

public interface GoalEvaluationRepository extends JpaRepository<GoalEvaluationEntity, UUID> {

    Optional<GoalEvaluationEntity> findByGoalIdAndPeriodStart(UUID goalId, LocalDate periodStart);

    /**
     * Evaluations carry no user_id of their own — they are reachable only through
     * their goal, so both the export and the erasure address them by goal id.
     */
    List<GoalEvaluationEntity> findByGoalIdInOrderByPeriodStartAsc(Collection<UUID> goalIds);

    long deleteByGoalIdIn(Collection<UUID> goalIds);
}
