package com.personalfinance.user.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * The creation response — the only place the raw token is ever returned.
 *
 * Nothing persists the {@code token} field: it exists for the duration of this
 * response and is then unrecoverable, so the client must store it or lose it.
 */
@Data
@Builder
public class PersonalAccessTokenCreatedDto {

    private UUID id;
    private String name;
    private String scope;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;

    /** Shown once, at creation. Never logged, never re-served. */
    private String token;
}
