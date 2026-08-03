package com.personalfinance.expense.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "recurring_expenses")
public class RecurringExpenseEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    /** Bani. */
    @Column(nullable = false)
    private long amount;

    private String note;

    /** Anchor day; clamped to shorter months (31 -> Feb 28) and recovers after. */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "next_run", nullable = false)
    private LocalDate nextRun;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecurringExpenseEntity() {
    }

    public RecurringExpenseEntity(UUID userId, UUID categoryId, long amount, String note, LocalDate startDate) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.note = note;
        this.dayOfMonth = startDate.getDayOfMonth();
        this.nextRun = startDate;
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

    public UUID getCategoryId() {
        return categoryId;
    }

    public long getAmount() {
        return amount;
    }

    public String getNote() {
        return note;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public LocalDate getNextRun() {
        return nextRun;
    }

    public boolean isActive() {
        return active;
    }

    /** Advances to the anchor day of the following month, clamped to its length. */
    public void advance() {
        LocalDate nextMonth = nextRun.plusMonths(1).withDayOfMonth(1);
        nextRun = nextMonth.withDayOfMonth(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
    }
}
