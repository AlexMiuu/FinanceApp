package com.personalfinance.quest.service;

import java.time.LocalDate;

import com.personalfinance.quest.entity.GoalEntity;

/**
 * A goal plus its current-period progress. Service-layer only — it carries the
 * entity, so it is the mapper's input, never a response body.
 */
public record GoalStatus(GoalEntity goal, long currentActual, boolean currentMet,
        LocalDate periodStart, LocalDate periodEnd) {
}
