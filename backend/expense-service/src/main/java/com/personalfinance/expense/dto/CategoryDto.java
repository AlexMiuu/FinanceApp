package com.personalfinance.expense.dto;

import java.util.UUID;

/**
 * Component name {@code isMandatory} is deliberate: it is the JSON field the
 * web client reads, and renaming it to {@code mandatory} would break the API.
 */
public record CategoryDto(UUID id, String name, UUID parentId, boolean isMandatory) {
}
