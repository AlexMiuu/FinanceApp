package com.personalfinance.quest.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code expiresAt} is an absolute instant rather than a duration: the deadline
 * a user pledges against is a wall-clock moment ("before I leave the shop at
 * 6pm"), and an absolute value survives a slow request without drifting.
 */
public record OathCreateRequestDto(
        @NotNull UUID categoryId,
        @Positive long pledgedAmount,
        @NotNull Instant expiresAt) {
}
