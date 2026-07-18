package com.personalfinance.user.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published whenever income sources change; carries the normalized monthly
 * income so quest-service can tailor quests without calling back (O3).
 */
public record IncomeUpdatedEvent(UUID userId, long monthlyIncome, Instant occurredAt) {
}
