package com.personalfinance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the owner supplies when minting a token: a label to recognise it by. */
@Data
@NoArgsConstructor
public class PersonalAccessTokenRequestDto {

    @NotBlank(message = "token name is required")
    @Size(max = 100, message = "token name must be at most 100 characters")
    private String name;
}
