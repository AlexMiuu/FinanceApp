package com.personalfinance.report.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.report.dto.CategoryProjectionDto;
import com.personalfinance.report.dto.ExpenseProjectionDto;
import com.personalfinance.report.entity.CategoryProjectionEntity;
import com.personalfinance.report.entity.ExpenseProjectionEntity;

/** Entity -> DTO translation for the projection read models, used only by the GDPR export. */
@Component
public class ProjectionExportMapper {

    public CategoryProjectionDto toDto(CategoryProjectionEntity entity) {
        return new CategoryProjectionDto(entity.getCategoryId(), entity.getName(), entity.getParentId(),
                entity.isMandatory());
    }

    public List<CategoryProjectionDto> toCategoryDtos(List<CategoryProjectionEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }

    public ExpenseProjectionDto toDto(ExpenseProjectionEntity entity) {
        return new ExpenseProjectionDto(entity.getExpenseId(), entity.getCategoryId(), entity.getCategoryPath(),
                entity.isMandatory(), entity.getAmount(), entity.getCurrency(), entity.getNote(),
                entity.getExpenseDate());
    }

    public List<ExpenseProjectionDto> toExpenseDtos(List<ExpenseProjectionEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
