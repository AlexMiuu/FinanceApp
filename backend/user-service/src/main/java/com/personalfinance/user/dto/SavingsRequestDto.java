package com.personalfinance.user.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized   // @Builder leaves no no-arg constructor; this points Jackson at the builder
public class SavingsRequestDto {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @PositiveOrZero
    private Long balance;
}
