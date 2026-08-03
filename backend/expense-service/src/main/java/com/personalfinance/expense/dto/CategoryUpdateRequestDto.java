package com.personalfinance.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Update cannot reparent a category, so it carries no parentId.
 */
public record CategoryUpdateRequestDto(
        @NotBlank @Size(max = 60) String name,
        boolean isMandatory) {
}
