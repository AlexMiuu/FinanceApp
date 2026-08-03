package com.personalfinance.expense.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.expense.dto.ExpenseDto;
import com.personalfinance.expense.entity.ExpenseEntity;

/** Entity -> DTO translation for expenses. */
@Component
public class ExpenseMapper {

    public ExpenseDto toDto(ExpenseEntity entity) {
        return new ExpenseDto(
                entity.getId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getCategoryId(),
                entity.getNote(),
                entity.getExpenseDate());
    }

    public List<ExpenseDto> toDtos(List<ExpenseEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
