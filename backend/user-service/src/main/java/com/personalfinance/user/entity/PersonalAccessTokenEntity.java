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

/**
 * A personal access token for the read-only developer API (M14).
 *
 * The entity never holds the raw token — only its SHA-256 hex digest. The
 * caller generates the token, hands the hash here, and shows the raw value to
 * the user once; nothing in this service can recover it afterwards.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "personal_access_tokens")
public class PersonalAccessTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** A user-chosen label, so a token can be identified in the list without revealing it. */
    @Column(nullable = false)
    private String name;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private String scope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public PersonalAccessTokenEntity(UUID userId, String name, String tokenHash, String scope) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.scope = scope;
        this.createdAt = Instant.now();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
    }

    /**
     * Idempotent: re-revoking keeps the original timestamp, so a repeated DELETE
     * cannot rewrite when access actually ended.
     */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }
}
