package com.personalfinance.expense.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.expense.dto.RecurringExpenseDto;
import com.personalfinance.expense.entity.RecurringExpenseEntity;

/** Entity -> DTO translation for recurring expense templates. */
@Component
public class RecurringExpenseMapper {

    public RecurringExpenseDto toDto(RecurringExpenseEntity entity) {
        return new RecurringExpenseDto(
                entity.getId(),
                entity.getCategoryId(),
                entity.getAmount(),
                entity.getNote(),
                entity.getDayOfMonth(),
                entity.getNextRun(),
                entity.isActive());
    }

    public List<RecurringExpenseDto> toDtos(List<RecurringExpenseEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
