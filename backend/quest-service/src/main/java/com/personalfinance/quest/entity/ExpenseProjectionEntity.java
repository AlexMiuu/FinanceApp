package com.personalfinance.quest.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Event-fed read model; one row per expense. Upserted, never authored here. */
@Entity
@Table(name = "expense_projection")
public class ExpenseProjectionEntity {

    @Id
    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_path", nullable = false)
    private String categoryPath;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency = "RON";

    private String note;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ExpenseProjectionEntity() {
    }

    public ExpenseProjectionEntity(UUID expenseId, UUID userId, UUID categoryId, String categoryPath,
            boolean mandatory, long amount, String currency, String note, LocalDate expenseDate) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.categoryId = categoryId;
        this.categoryPath = categoryPath;
        this.mandatory = mandatory;
        this.amount = amount;
        this.currency = currency;
        this.note = note;
        this.expenseDate = expenseDate;
        this.updatedAt = Instant.now();
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getCategoryPath() {
        return categoryPath;
    }

    public boolean isMandatory() {
        return mandatory;
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
}
