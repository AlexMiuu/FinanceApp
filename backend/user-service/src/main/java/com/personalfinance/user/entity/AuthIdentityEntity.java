package com.personalfinance.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
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

    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "role_id", referencedColumnName = "id", nullable = false)
    private Role role;

    protected AuthIdentityEntity() {
    }

    public AuthIdentityEntity(UserEntity user, String provider, String providerUid, Role role) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerUid = providerUid;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

}
