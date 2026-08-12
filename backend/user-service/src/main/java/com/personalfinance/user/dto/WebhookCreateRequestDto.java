package com.personalfinance.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebhookCreateRequestDto(
        @NotBlank @Size(max = 2000) String url,
        @NotBlank @Size(max = 255) String eventPattern) {
}
