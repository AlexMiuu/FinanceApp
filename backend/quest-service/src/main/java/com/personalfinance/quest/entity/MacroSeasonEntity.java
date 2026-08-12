package com.personalfinance.quest.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Local projection of macro.season.changed. Seasonal quest generation happens on
 * ordinary reads too, not only on the event, so the season has to be readable
 * without replaying the exchange.
 */
@Entity
@Table(name = "macro_season")
public class MacroSeasonEntity {

    public static final int SINGLETON_ID = 1;

    @Id
    private int id = SINGLETON_ID;

    @Column(nullable = false)
    private String season;

    private String source;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MacroSeasonEntity() {
    }

    public MacroSeasonEntity(String season, String source, LocalDate asOfDate) {
        this.id = SINGLETON_ID;
        this.season = season;
        this.source = source;
        this.asOfDate = asOfDate;
        this.updatedAt = Instant.now();
    }

    public String getSeason() {
        return season;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
