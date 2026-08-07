package com.personalfinance.quest.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GoalRequestDto(
        @NotBlank @Size(max = 100) String name,
        UUID categoryId,
        @Positive long targetAmount,
        @NotNull @Pattern(regexp = "DAILY|MONTHLY|YEARLY") String period,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean active) {

    /** Absent means "leave it enabled" — preserves the pre-M9 controller default. */
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
