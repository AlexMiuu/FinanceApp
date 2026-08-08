package com.personalfinance.quest.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "oaths")
public class OathEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "pledged_amount", nullable = false)
    private long pledgedAmount;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "matched_expense_id")
    private UUID matchedExpenseId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OathEntity() {
    }

    public OathEntity(UUID userId, UUID categoryId, String categoryName, long pledgedAmount,
            Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.pledgedAmount = pledgedAmount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.updatedAt = createdAt;
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

    public String getCategoryName() {
        return categoryName;
    }

    public long getPledgedAmount() {
        return pledgedAmount;
    }

    public String getStatus() {
        return status;
    }

    public UUID getMatchedExpenseId() {
        return matchedExpenseId;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
