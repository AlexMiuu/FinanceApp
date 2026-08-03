package com.personalfinance.expense.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequestDto(
        @NotBlank @Size(max = 60) String name,
        UUID parentId,
        boolean isMandatory) {
}
