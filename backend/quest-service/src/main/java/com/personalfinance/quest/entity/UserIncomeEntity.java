package com.personalfinance.quest.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_income")
public class UserIncomeEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "monthly_income", nullable = false)
    private long monthlyIncome;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserIncomeEntity() {
    }

    public UserIncomeEntity(UUID userId, long monthlyIncome) {
        this.userId = userId;
        this.monthlyIncome = monthlyIncome;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public long getMonthlyIncome() {
        return monthlyIncome;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
