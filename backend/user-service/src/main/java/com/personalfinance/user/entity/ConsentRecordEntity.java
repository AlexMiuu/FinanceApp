package com.personalfinance.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Evidence that a user accepted a specific version of the terms and privacy
 * policy (GDPR Art. 7(1)). Immutable once written: withdrawing consent is a new
 * record or an erasure, never an edit of the original.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "consent_records")
public class ConsentRecordEntity {

    public static final String TYPE_TOS_PRIVACY = "TOS_PRIVACY";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "consent_type", nullable = false)
    private String consentType;

    @Column(nullable = false)
    private String version;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public ConsentRecordEntity(UUID userId, String consentType, String version, Instant grantedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.consentType = consentType;
        this.version = version;
        this.grantedAt = grantedAt;
    }

    @PrePersist
    void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
}
