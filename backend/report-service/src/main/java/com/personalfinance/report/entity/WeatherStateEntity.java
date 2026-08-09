package com.personalfinance.report.entity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The committed band plus the dwell clock of a transition that has been earned
 * but not yet served its time. A pending band is not user-visible: only
 * {@code currentBand} is ever published or served.
 */
@Entity
@Table(name = "weather_state")
public class WeatherStateEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_band", nullable = false)
    private WeatherBand currentBand = WeatherBand.CLEAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "pending_band")
    private WeatherBand pendingBand;

    @Column(name = "pending_since")
    private Instant pendingSince;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WeatherStateEntity() {
    }

    public WeatherStateEntity(UUID userId) {
        this.userId = userId;
        this.currentBand = WeatherBand.CLEAR;
        this.updatedAt = Instant.now();
    }

    public void startPending(WeatherBand band, Instant at) {
        this.pendingBand = band;
        this.pendingSince = at;
        this.updatedAt = at;
    }

    public void clearPending(Instant at) {
        this.pendingBand = null;
        this.pendingSince = null;
        this.updatedAt = at;
    }

    public void commit(WeatherBand band, Instant at) {
        this.currentBand = band;
        this.pendingBand = null;
        this.pendingSince = null;
        this.updatedAt = at;
    }

    public UUID getUserId() {
        return userId;
    }

    public WeatherBand getCurrentBand() {
        return currentBand;
    }

    public Optional<WeatherBand> getPendingBand() {
        return Optional.ofNullable(pendingBand);
    }

    public Optional<Instant> getPendingSince() {
        return Optional.ofNullable(pendingSince);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
