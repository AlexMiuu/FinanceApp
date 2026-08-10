package com.personalfinance.user.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

/**
 * A personal access token as the owner sees it in their token list.
 *
 * Deliberately carries neither the token nor its hash: once created, a token is
 * identified by its label and id only. {@link PersonalAccessTokenCreatedDto} is
 * the single response shape that ever includes the secret.
 */
@Data
@Builder
public class PersonalAccessTokenDto {

    private UUID id;
    private String name;
    private String scope;
    private Instant createdAt;

    /** Null until the token has authenticated a request at least once. */
    private Instant lastUsedAt;

    /** Null while the token is live; set once revoked, and never cleared. */
    private Instant revokedAt;
}
