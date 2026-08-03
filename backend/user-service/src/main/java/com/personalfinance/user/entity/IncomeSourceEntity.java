package com.personalfinance.user.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;

@Entity
@Table(name = "income_sources")
public class IncomeSourceEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    /** Bani. */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String recurrence;   // MONTHLY | YEARLY | ONE_OFF

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IncomeSourceEntity() {
    }

    @Builder
    public IncomeSourceEntity(UUID userId, String name, long amount, String recurrence,
            LocalDate startDate, LocalDate endDate) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.amount = amount;
        this.recurrence = recurrence;
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

    public String getName() {
        return name;
    }

    public long getAmount() {
        return amount;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void update(String name, long amount, String recurrence, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.amount = amount;
        this.recurrence = recurrence;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
