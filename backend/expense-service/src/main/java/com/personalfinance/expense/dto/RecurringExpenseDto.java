package com.personalfinance.expense.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RecurringExpenseDto(
        UUID id,
        UUID categoryId,
        long amount,
        String note,
        int dayOfMonth,
        LocalDate nextRun,
        boolean active) {
}
