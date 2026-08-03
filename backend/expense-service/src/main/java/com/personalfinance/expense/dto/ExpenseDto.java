package com.personalfinance.expense.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Amounts are in bani (RON cents), matching the entity. */
public record ExpenseDto(
        UUID id,
        long amount,
        String currency,
        UUID categoryId,
        String note,
        LocalDate expenseDate) {
}
