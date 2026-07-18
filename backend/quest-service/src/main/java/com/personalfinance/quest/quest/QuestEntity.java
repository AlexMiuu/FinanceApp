package com.personalfinance.quest.quest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "quests")
public class QuestEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> params;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private String status = "SUGGESTED";

    @Column(name = "progress_amount", nullable = false)
    private long progressAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuestEntity() {
    }

    public QuestEntity(UUID userId, String templateCode, String title, Map<String, Object> params,
            LocalDate periodStart, LocalDate periodEnd) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.templateCode = templateCode;
        this.title = title;
        this.params = params;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
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

    public String getTemplateCode() {
        return templateCode;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getStatus() {
        return status;
    }

    public long getProgressAmount() {
        return progressAmount;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setProgressAmount(long progressAmount) {
        this.progressAmount = progressAmount;
    }

    public long paramLong(String key) {
        return ((Number) params.getOrDefault(key, 0)).longValue();
    }

    public UUID paramUuid(String key) {
        Object value = params.get(key);
        return value == null ? null : UUID.fromString(value.toString());
    }
}
