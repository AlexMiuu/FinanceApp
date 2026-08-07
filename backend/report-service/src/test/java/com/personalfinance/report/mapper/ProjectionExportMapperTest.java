package com.personalfinance.report.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.report.dto.CategoryProjectionDto;
import com.personalfinance.report.dto.ExpenseProjectionDto;
import com.personalfinance.report.entity.CategoryProjectionEntity;
import com.personalfinance.report.entity.ExpenseProjectionEntity;

class ProjectionExportMapperTest {

    private final ProjectionExportMapper mapper = new ProjectionExportMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void copiesEveryExposedCategoryField() {
        CategoryProjectionEntity entity = new CategoryProjectionEntity(
                UUID.randomUUID(), userId, "Housing", null, true);

        CategoryProjectionDto dto = mapper.toDto(entity);

        assertThat(dto.categoryId()).isEqualTo(entity.getCategoryId());
        assertThat(dto.name()).isEqualTo("Housing");
        assertThat(dto.parentId()).isNull();
        assertThat(dto.mandatory()).isTrue();
    }

    @Test
    void copiesEveryExposedExpenseField() {
        ExpenseProjectionEntity entity = new ExpenseProjectionEntity(
                UUID.randomUUID(), userId, UUID.randomUUID(), "Food > Groceries", false, 4550, "RON",
                "weekly shop", LocalDate.of(2026, 7, 10));

        ExpenseProjectionDto dto = mapper.toDto(entity);

        assertThat(dto.expenseId()).isEqualTo(entity.getExpenseId());
        assertThat(dto.categoryPath()).isEqualTo("Food > Groceries");
        assertThat(dto.amount()).isEqualTo(4550);
        assertThat(dto.note()).isEqualTo("weekly shop");
    }

    @Test
    void mapsEmptyListsToEmptyLists() {
        assertThat(mapper.toCategoryDtos(List.of())).isEmpty();
        assertThat(mapper.toExpenseDtos(List.of())).isEmpty();
    }
}
