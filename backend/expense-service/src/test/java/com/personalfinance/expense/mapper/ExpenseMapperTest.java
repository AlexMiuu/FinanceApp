package com.personalfinance.expense.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.expense.dto.ExpenseDto;
import com.personalfinance.expense.entity.ExpenseEntity;

class ExpenseMapperTest {

    private final ExpenseMapper mapper = new ExpenseMapper();
    private final UUID userId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Test
    void copiesEveryExposedField() {
        ExpenseEntity expense = new ExpenseEntity(userId, categoryId, 12345, "rent",
                LocalDate.of(2026, 7, 19));

        ExpenseDto dto = mapper.toDto(expense);

        assertThat(dto.id()).isEqualTo(expense.getId());
        assertThat(dto.amount()).isEqualTo(12345);
        assertThat(dto.currency()).isEqualTo("RON");
        assertThat(dto.categoryId()).isEqualTo(categoryId);
        assertThat(dto.note()).isEqualTo("rent");
        assertThat(dto.expenseDate()).isEqualTo(LocalDate.of(2026, 7, 19));
    }

    @Test
    void toleratesAMissingNote() {
        ExpenseEntity expense = new ExpenseEntity(userId, categoryId, 1, null, LocalDate.of(2026, 7, 19));

        assertThat(mapper.toDto(expense).note()).isNull();
    }

    /** The owning user is an internal column and must stay out of the API. */
    @Test
    void neverLeaksTheOwningUser() {
        assertThat(ExpenseDto.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("userId");
    }

    @Test
    void mapsAnEmptyListToAnEmptyList() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
    }
}
