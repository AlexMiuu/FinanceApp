package com.personalfinance.report.macro.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single globally-committed pastoral season (F1). Unlike F4's weather,
 * this is not per-user — the calendar is the same for everyone — so there is
 * exactly one row, read and written like quest-service's mirror of the same
 * concept ({@code MacroSeasonEntity}).
 */
@Entity
@Table(name = "macro_season_state")
public class MacroSeasonStateEntity {

    public static final int SINGLETON_ID = 1;

    @Id
    private int id = SINGLETON_ID;

    @Column(nullable = false)
    private String season;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MacroSeasonStateEntity() {
    }

    public MacroSeasonStateEntity(String season, LocalDate asOfDate) {
        this.id = SINGLETON_ID;
        this.season = season;
        this.asOfDate = asOfDate;
        this.updatedAt = Instant.now();
    }

    public String getSeason() {
        return season;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
