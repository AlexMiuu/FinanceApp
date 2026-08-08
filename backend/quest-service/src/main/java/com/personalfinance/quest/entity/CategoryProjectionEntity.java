package com.personalfinance.quest.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category_projection")
public class CategoryProjectionEntity {

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    protected CategoryProjectionEntity() {
    }

    public CategoryProjectionEntity(UUID categoryId, UUID userId, String name, UUID parentId, boolean mandatory) {
        this.categoryId = categoryId;
        this.userId = userId;
        this.name = name;
        this.parentId = parentId;
        this.mandatory = mandatory;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public UUID getParentId() {
        return parentId;
    }

    public boolean isMandatory() {
        return mandatory;
    }
}
