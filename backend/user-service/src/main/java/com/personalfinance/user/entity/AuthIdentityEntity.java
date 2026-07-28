package com.personalfinance.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_identities")
public class AuthIdentityEntity {

    public static final String PROVIDER_PASSWORD = "PASSWORD";
    public static final String PROVIDER_GOOGLE = "GOOGLE";

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_uid", nullable = false)
    private String providerUid;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthIdentityEntity() {
    }

    public AuthIdentityEntity(UserEntity user, String provider, String providerUid) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerUid = providerUid;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UserEntity getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUid() {
        return providerUid;
    }
}
