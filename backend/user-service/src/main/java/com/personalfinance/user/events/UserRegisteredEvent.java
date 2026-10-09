package com.personalfinance.user.events;

import com.personalfinance.user.entity.Role;

import java.time.Instant;
import java.util.UUID;

/** Published when a new account is created (password or Google). */
public record UserRegisteredEvent(UUID userId, String email, String displayName, Instant occurredAt, Role role) {
}
