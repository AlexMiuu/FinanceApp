package com.personalfinance.expense.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.expense.dto.CategoryDto;
import com.personalfinance.expense.entity.CategoryEntity;

class CategoryMapperTest {

    private final CategoryMapper mapper = new CategoryMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void copiesEveryExposedField() {
        CategoryEntity parent = new CategoryEntity(userId, null, "Housing", true);
        CategoryEntity child = new CategoryEntity(userId, parent.getId(), "Rent", false);

        CategoryDto dto = mapper.toDto(child);

        assertThat(dto.id()).isEqualTo(child.getId());
        assertThat(dto.name()).isEqualTo("Rent");
        assertThat(dto.parentId()).isEqualTo(parent.getId());
        assertThat(dto.isMandatory()).isFalse();
    }

    @Test
    void mapsAnEmptyListToAnEmptyList() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
    }

    @Test
    void preservesOrder() {
        CategoryEntity food = new CategoryEntity(userId, null, "Food", false);
        CategoryEntity housing = new CategoryEntity(userId, null, "Housing", true);

        assertThat(mapper.toDtos(List.of(food, housing)))
                .extracting(CategoryDto::name)
                .containsExactly("Food", "Housing");
    }
}
