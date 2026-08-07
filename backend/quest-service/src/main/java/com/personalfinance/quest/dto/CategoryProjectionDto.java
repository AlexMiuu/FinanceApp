package com.personalfinance.quest.dto;

import java.util.UUID;

public record CategoryProjectionDto(UUID categoryId, String name, UUID parentId, boolean mandatory) {
}
