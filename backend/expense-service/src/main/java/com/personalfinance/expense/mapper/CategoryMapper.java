package com.personalfinance.expense.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.expense.dto.CategoryDto;
import com.personalfinance.expense.entity.CategoryEntity;

/** Entity -> DTO translation for categories. */
@Component
public class CategoryMapper {

    public CategoryDto toDto(CategoryEntity entity) {
        return new CategoryDto(
                entity.getId(),
                entity.getName(),
                entity.getParentId(),
                entity.isMandatory());
    }

    public List<CategoryDto> toDtos(List<CategoryEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
