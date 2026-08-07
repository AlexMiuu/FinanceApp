package com.personalfinance.quest.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.quest.dto.QuestDto;
import com.personalfinance.quest.dto.QuestExportDto;
import com.personalfinance.quest.dto.UserIncomeDto;
import com.personalfinance.quest.entity.QuestEntity;
import com.personalfinance.quest.entity.UserIncomeEntity;
import com.personalfinance.quest.service.QuestView;

/** Entity -&gt; DTO translation for quests and the income projection they are tailored from. */
@Component
public class QuestMapper {

    public QuestDto toDto(QuestView view) {
        QuestEntity quest = view.quest();
        return new QuestDto(quest.getId(), quest.getTemplateCode(), quest.getTitle(), quest.getStatus(),
                view.kind(), view.target(), view.progress(),
                quest.getPeriodStart().toString(), quest.getPeriodEnd().toString(), quest.getParams());
    }

    public List<QuestDto> toDtos(List<QuestView> views) {
        return views.stream().map(this::toDto).toList();
    }

    public QuestExportDto toExportDto(QuestEntity entity) {
        return new QuestExportDto(entity.getId(), entity.getTemplateCode(), entity.getTitle(),
                entity.getParams(), entity.getPeriodStart(), entity.getPeriodEnd(), entity.getStatus(),
                entity.getProgressAmount(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public List<QuestExportDto> toExportDtos(List<QuestEntity> entities) {
        return entities.stream().map(this::toExportDto).toList();
    }

    public UserIncomeDto toIncomeDto(UserIncomeEntity entity) {
        return new UserIncomeDto(entity.getMonthlyIncome(), entity.getUpdatedAt());
    }
}
