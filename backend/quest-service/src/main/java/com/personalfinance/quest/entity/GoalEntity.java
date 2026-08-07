package com.personalfinance.quest.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "goals")
public class GoalEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type = "SPENDING_LIMIT";

    @Column(name = "category_id")
    private UUID categoryId;   // NULL = overall spending

    /** Bani. */
    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    @Column(nullable = false)
    private String period;   // DAILY | MONTHLY | YEARLY

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GoalEntity() {
    }

    public GoalEntity(UUID userId, String name, UUID categoryId, long targetAmount, String period,
            LocalDate startDate, LocalDate endDate) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.categoryId = categoryId;
        this.targetAmount = targetAmount;
        this.period = period;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public String getPeriod() {
        return period;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Whether the goal applies on the given date. */
    public boolean appliesOn(LocalDate date) {
        return active && !startDate.isAfter(date) && (endDate == null || !endDate.isBefore(date));
    }

    public void update(String name, UUID categoryId, long targetAmount, String period,
            LocalDate startDate, LocalDate endDate, boolean active) {
        this.name = name;
        this.categoryId = categoryId;
        this.targetAmount = targetAmount;
        this.period = period;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
    }
}
