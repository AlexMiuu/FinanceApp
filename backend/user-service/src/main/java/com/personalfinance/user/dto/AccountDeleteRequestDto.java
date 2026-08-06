package com.personalfinance.user.dto;


import jakarta.validation.constraints.AssertTrue;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized   // @Builder leaves no no-arg constructor; this points Jackson at the builder
public class AccountDeleteRequestDto {

    /** Erasure is irreversible, so the client must say so explicitly. */
    @AssertTrue(message = "must be true to confirm irreversible account deletion")
    private boolean confirm;
}
