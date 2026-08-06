package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AuthIdentityDto {

    private String provider;
    private String providerUid;
    private Instant createdAt;
}
