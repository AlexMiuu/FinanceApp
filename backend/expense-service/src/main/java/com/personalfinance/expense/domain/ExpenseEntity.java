package com.personalfinance.expense.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "expenses")
public class ExpenseEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** Amount in bani (RON cents). */
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency = "RON";

    private String note;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false)
    private String source = "MANUAL";

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExpenseEntity() {
    }

    public ExpenseEntity(UUID userId, UUID categoryId, long amount, String note, LocalDate expenseDate) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.note = note;
        this.expenseDate = expenseDate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void update(UUID categoryId, long amount, String note, LocalDate expenseDate) {
        this.categoryId = categoryId;
        this.amount = amount;
        this.note = note;
        this.expenseDate = expenseDate;
    }
}
