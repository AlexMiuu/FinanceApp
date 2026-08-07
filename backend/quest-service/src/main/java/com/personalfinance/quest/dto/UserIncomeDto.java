package com.personalfinance.quest.dto;

import java.time.Instant;

public record UserIncomeDto(long monthlyIncome, Instant updatedAt) {
}
