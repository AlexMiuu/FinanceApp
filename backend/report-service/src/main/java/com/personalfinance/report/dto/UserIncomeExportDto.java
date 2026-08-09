package com.personalfinance.report.dto;

import java.time.Instant;

public record UserIncomeExportDto(long monthlyIncome, Instant updatedAt) {
}
