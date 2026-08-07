package com.personalfinance.quest.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persisted result for a completed period (UNIQUE goal_id + period_start). */
@Entity
@Table(name = "goal_evaluations")
public class GoalEvaluationEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "actual_amount", nullable = false)
    private long actualAmount;

    @Column(nullable = false)
    private boolean met;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected GoalEvaluationEntity() {
    }

    public GoalEvaluationEntity(UUID goalId, LocalDate periodStart, LocalDate periodEnd,
            long actualAmount, boolean met) {
        this.id = UUID.randomUUID();
        this.goalId = goalId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.actualAmount = actualAmount;
        this.met = met;
        this.evaluatedAt = Instant.now();
    }

    public void refresh(long actualAmount, boolean met) {
        this.actualAmount = actualAmount;
        this.met = met;
        this.evaluatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public long getActualAmount() {
        return actualAmount;
    }

    public boolean isMet() {
        return met;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
