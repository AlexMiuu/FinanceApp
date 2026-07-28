package com.personalfinance.user.dto;


import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class IncomeRequestDto {
    @NotBlank
    @Size(max = 100)
    public String name;

    @Positive
    public long amount;

    @NotNull
    @Pattern(regexp = "MONTHLY|YEARLY|ONE_OFF")
    public String recurrence;

    @NotNull
    public LocalDate startDate;

    @NotNull
    public LocalDate endDate;
}
