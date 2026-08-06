package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything User Service holds about one person, in a portable form
 * (GDPR Art. 20). Each data class here must appear in the written inventory.
 */
@Data
@Builder
public class DataExportDto {

    private UUID userId;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String baseCurrency;
    private Instant createdAt;
    private Instant exportedAt;

    private List<IncomeDto> incomeSources;
    private List<SavingsDto> savingsAccounts;
    private List<ConsentRecordDto> consentRecords;
    private List<AuthIdentityDto> authIdentities;
    private List<RefreshTokenDto> refreshTokens;
}
