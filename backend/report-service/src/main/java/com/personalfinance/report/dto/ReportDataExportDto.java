package com.personalfinance.report.dto;

import java.util.List;

public record ReportDataExportDto(
        List<CategoryProjectionDto> categoryProjections,
        List<ExpenseProjectionDto> expenseProjections,
        List<ReportDto> reports,
        UserIncomeExportDto userIncome,
        WeatherStateExportDto weatherState) {
}
