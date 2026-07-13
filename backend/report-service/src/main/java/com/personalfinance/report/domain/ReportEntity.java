package com.personalfinance.report.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "reports")
public class ReportEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    /** Saved filter definition: {from, to, categoryIds} — re-evaluated on each run (FR-3). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> filters;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cached_result")
    private Map<String, Object> cachedResult;

    protected ReportEntity() {
    }

    public ReportEntity(UUID userId, String name, Map<String, Object> filters) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.filters = filters;
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

    public Map<String, Object> getFilters() {
        return filters;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public Map<String, Object> getCachedResult() {
        return cachedResult;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    public void recordRun(Map<String, Object> result) {
        this.cachedResult = result;
        this.lastRunAt = Instant.now();
    }
}
