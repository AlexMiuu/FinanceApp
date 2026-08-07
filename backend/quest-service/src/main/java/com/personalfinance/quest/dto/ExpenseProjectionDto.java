package com.personalfinance.quest.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ExpenseProjectionDto(UUID expenseId, UUID categoryId, String categoryPath,
        boolean mandatory, long amount, String currency, String note, LocalDate expenseDate) {
}
