package com.personalfinance.quest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.quest.entity.CategoryProjectionEntity;
import com.personalfinance.quest.entity.ExpenseProjectionEntity;

class ProjectionExportMapperTest {

    private final ProjectionExportMapper mapper = new ProjectionExportMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void aCategoryProjectionMapsEveryColumnTheExportPromises() {
        UUID categoryId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        CategoryProjectionEntity entity =
                new CategoryProjectionEntity(categoryId, userId, "Groceries", parentId, true);

        assertThat(mapper.toDto(entity)).satisfies(dto -> {
            assertThat(dto.categoryId()).isEqualTo(categoryId);
            assertThat(dto.name()).isEqualTo("Groceries");
            assertThat(dto.parentId()).isEqualTo(parentId);
            assertThat(dto.mandatory()).isTrue();
        });
    }

    @Test
    void aTopLevelCategoryExportsANullParent() {
        CategoryProjectionEntity entity =
                new CategoryProjectionEntity(UUID.randomUUID(), userId, "Food", null, false);

        assertThat(mapper.toDto(entity).parentId()).isNull();
    }

    @Test
    void anExpenseProjectionMapsEveryColumnTheExportPromises() {
        UUID expenseId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ExpenseProjectionEntity entity = new ExpenseProjectionEntity(expenseId, userId, categoryId,
                "Food > Groceries", false, 12345, "RON", "weekly shop", LocalDate.of(2026, 7, 1));

        assertThat(mapper.toDto(entity)).satisfies(dto -> {
            assertThat(dto.expenseId()).isEqualTo(expenseId);
            assertThat(dto.categoryId()).isEqualTo(categoryId);
            assertThat(dto.categoryPath()).isEqualTo("Food > Groceries");
            assertThat(dto.mandatory()).isFalse();
            assertThat(dto.amount()).isEqualTo(12345);
            assertThat(dto.currency()).isEqualTo("RON");
            assertThat(dto.note()).isEqualTo("weekly shop");
            assertThat(dto.expenseDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        });
    }

    @Test
    void anExpenseWithoutANoteExportsANullNoteRatherThanFailing() {
        ExpenseProjectionEntity entity = new ExpenseProjectionEntity(UUID.randomUUID(), userId,
                UUID.randomUUID(), "Food", false, 100, "RON", null, LocalDate.of(2026, 7, 1));

        assertThat(mapper.toDto(entity).note()).isNull();
    }

    @Test
    void bothCollectionMappersMapEmptyToEmpty() {
        assertThat(mapper.toCategoryDtos(List.of())).isEmpty();
        assertThat(mapper.toExpenseDtos(List.of())).isEmpty();
    }
}
