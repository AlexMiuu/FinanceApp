package com.personalfinance.user.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized   // @Builder leaves no no-arg constructor; this points Jackson at the builder
public class SalaryRequestDto {

    public static final String GROSS_TO_NET = "GROSS_TO_NET";
    public static final String NET_TO_GROSS = "NET_TO_GROSS";

    @NotNull
    @Pattern(regexp = GROSS_TO_NET + "|" + NET_TO_GROSS)
    private String mode;

    @NotNull
    @Positive
    private Long amount;
}
