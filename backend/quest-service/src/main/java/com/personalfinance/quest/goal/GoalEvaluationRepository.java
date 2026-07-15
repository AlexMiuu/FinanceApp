package com.personalfinance.quest.goal;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalEvaluationRepository extends JpaRepository<GoalEvaluationEntity, UUID> {

    Optional<GoalEvaluationEntity> findByGoalIdAndPeriodStart(UUID goalId, LocalDate periodStart);
}
