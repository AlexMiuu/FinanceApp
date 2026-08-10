package com.personalfinance.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One endpoint a user has asked to be notified on, and its delivery health. */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "webhook_subscriptions")
public class WebhookSubscriptionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String url;

    @Column(name = "event_pattern", nullable = false)
    private String eventPattern;

    @Column(nullable = false)
    private String secret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    public WebhookSubscriptionEntity(UUID userId, String url, String eventPattern, String secret) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.url = url;
        this.eventPattern = eventPattern;
        this.secret = secret;
        this.createdAt = Instant.now();
        this.consecutiveFailures = 0;
    }

    public boolean isLive() {
        return disabledAt == null;
    }

    public void recordDeliverySucceeded() {
        this.consecutiveFailures = 0;
    }

    /**
     * Counts one delivery that exhausted its retries and disables the
     * subscription once the endpoint looks permanently gone. Returns true when
     * this call is what disabled it, so the caller can log the transition once
     * rather than on every subsequent failure.
     */
    public boolean recordDeliveryFailed(int disableThreshold) {
        this.consecutiveFailures++;
        if (this.consecutiveFailures >= disableThreshold && disabledAt == null) {
            this.disabledAt = Instant.now();
            return true;
        }
        return false;
    }
}
