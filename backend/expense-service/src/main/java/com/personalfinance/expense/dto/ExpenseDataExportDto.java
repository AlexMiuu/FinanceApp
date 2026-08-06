package com.personalfinance.expense.dto;

import java.util.List;

public record ExpenseDataExportDto(
        List<CategoryDto> categories,
        List<ExpenseDto> expenses,
        List<RecurringExpenseDto> recurringExpenses) {
}
