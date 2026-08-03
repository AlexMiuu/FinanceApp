package com.personalfinance.expense.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RecurringExpenseRequestDto(
        @NotNull @Positive Long amount,
        @NotNull UUID categoryId,
        @Size(max = 500) String note,
        @NotNull LocalDate startDate) {
}
