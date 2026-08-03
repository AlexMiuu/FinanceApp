package com.personalfinance.expense.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.expense.dto.RecurringExpenseDto;
import com.personalfinance.expense.entity.RecurringExpenseEntity;

class RecurringExpenseMapperTest {

    private final RecurringExpenseMapper mapper = new RecurringExpenseMapper();
    private final UUID userId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Test
    void copiesEveryExposedField() {
        RecurringExpenseEntity template = new RecurringExpenseEntity(userId, categoryId, 180000, "rent",
                LocalDate.of(2026, 1, 31));

        RecurringExpenseDto dto = mapper.toDto(template);

        assertThat(dto.id()).isEqualTo(template.getId());
        assertThat(dto.categoryId()).isEqualTo(categoryId);
        assertThat(dto.amount()).isEqualTo(180000);
        assertThat(dto.note()).isEqualTo("rent");
        assertThat(dto.dayOfMonth()).isEqualTo(31);
        assertThat(dto.nextRun()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(dto.active()).isTrue();
    }

    @Test
    void reflectsTheClampedNextRunAfterAdvancing() {
        RecurringExpenseEntity template = new RecurringExpenseEntity(userId, categoryId, 5000, "sub",
                LocalDate.of(2026, 1, 31));
        template.advance();

        RecurringExpenseDto dto = mapper.toDto(template);

        assertThat(dto.dayOfMonth()).isEqualTo(31);
        assertThat(dto.nextRun()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void mapsAnEmptyListToAnEmptyList() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
    }
}
