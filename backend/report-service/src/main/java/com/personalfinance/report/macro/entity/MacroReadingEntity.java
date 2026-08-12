package com.personalfinance.report.macro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The last-known-good cached value for one macro series (F1). {@code kind} is
 * the primary key: exactly one cached reading per series, always overwritten
 * on a successful refresh, never on a failed one — a fetch failure leaves this
 * row untouched, which is what makes serving it "last-known-good" rather than
 * "whatever the last attempt produced."
 */
@Entity
@Table(name = "macro_readings")
public class MacroReadingEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private MacroReadingKind kind;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected MacroReadingEntity() {
    }

    public MacroReadingEntity(MacroReadingKind kind, BigDecimal value, LocalDate asOfDate) {
        this.kind = kind;
        this.value = value;
        this.asOfDate = asOfDate;
        this.fetchedAt = Instant.now();
    }

    public MacroReadingKind getKind() {
        return kind;
    }

    public BigDecimal getValue() {
        return value;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void update(BigDecimal value, LocalDate asOfDate) {
        this.value = value;
        this.asOfDate = asOfDate;
        this.fetchedAt = Instant.now();
    }
}
