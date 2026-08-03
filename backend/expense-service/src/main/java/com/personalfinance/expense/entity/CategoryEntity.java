package com.personalfinance.expense.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    private String icon;

    private String color;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CategoryEntity() {
    }

    public CategoryEntity(UUID userId, UUID parentId, String name, boolean mandatory) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.parentId = parentId;
        this.name = name;
        this.mandatory = mandatory;
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

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }
}
