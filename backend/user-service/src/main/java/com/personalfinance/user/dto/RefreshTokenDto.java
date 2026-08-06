package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** The token hash itself is a security credential, not portable data, and is deliberately excluded. */
@Data
@Builder
public class RefreshTokenDto {

    private UUID id;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean revoked;
}
