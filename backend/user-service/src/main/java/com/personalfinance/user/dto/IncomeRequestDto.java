package com.personalfinance.user.dto;


import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Data
@Builder
@Jacksonized   // @Builder leaves no no-arg constructor; this points Jackson at the builder
public class IncomeRequestDto {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @Positive
    private Long amount;

    @NotNull
    @Pattern(regexp = "MONTHLY|YEARLY|ONE_OFF")
    private String recurrence;

    @NotNull
    private LocalDate startDate;
    @NotNull
    private LocalDate endDate;
}
