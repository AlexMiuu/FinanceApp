package com.personalfinance.quest.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.quest.dto.GoalDto;
import com.personalfinance.quest.dto.GoalEvaluationDto;
import com.personalfinance.quest.dto.GoalExportDto;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.entity.GoalEvaluationEntity;
import com.personalfinance.quest.service.GoalStatus;

/** Entity -&gt; DTO translation for goals and their persisted evaluations. */
@Component
public class GoalMapper {

    public GoalDto toDto(GoalStatus status) {
        GoalEntity goal = status.goal();
        return new GoalDto(goal.getId(), goal.getName(), goal.getCategoryId(), goal.getTargetAmount(),
                goal.getPeriod(), goal.getStartDate(), goal.getEndDate(), goal.isActive(),
                status.currentActual(), status.currentMet(), status.periodStart(), status.periodEnd());
    }

    public List<GoalDto> toDtos(List<GoalStatus> statuses) {
        return statuses.stream().map(this::toDto).toList();
    }

    public GoalExportDto toExportDto(GoalEntity entity) {
        return new GoalExportDto(entity.getId(), entity.getName(), entity.getType(), entity.getCategoryId(),
                entity.getTargetAmount(), entity.getPeriod(), entity.getStartDate(), entity.getEndDate(),
                entity.isActive(), entity.getCreatedAt());
    }

    public List<GoalExportDto> toExportDtos(List<GoalEntity> entities) {
        return entities.stream().map(this::toExportDto).toList();
    }

    public GoalEvaluationDto toEvaluationDto(GoalEvaluationEntity entity) {
        return new GoalEvaluationDto(entity.getId(), entity.getGoalId(), entity.getPeriodStart(),
                entity.getPeriodEnd(), entity.getActualAmount(), entity.isMet(), entity.getEvaluatedAt());
    }

    public List<GoalEvaluationDto> toEvaluationDtos(List<GoalEvaluationEntity> entities) {
        return entities.stream().map(this::toEvaluationDto).toList();
    }
}
