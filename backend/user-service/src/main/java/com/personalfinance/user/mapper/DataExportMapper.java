package com.personalfinance.user.mapper;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.user.dto.AuthIdentityDto;
import com.personalfinance.user.dto.ConsentRecordDto;
import com.personalfinance.user.dto.DataExportDto;
import com.personalfinance.user.dto.RefreshTokenDto;
import com.personalfinance.user.entity.AuthIdentityEntity;
import com.personalfinance.user.entity.ConsentRecordEntity;
import com.personalfinance.user.entity.IncomeSourceEntity;
import com.personalfinance.user.entity.RefreshTokenEntity;
import com.personalfinance.user.entity.SavingsAccountEntity;
import com.personalfinance.user.entity.UserEntity;

import lombok.RequiredArgsConstructor;

/** Composes the per-user data export from the aggregates this service owns. */
@Component
@RequiredArgsConstructor
public class DataExportMapper {

    private final IncomeMapper incomeMapper;
    private final SavingsMapper savingsMapper;

    public DataExportDto toDto(UserEntity user,
            List<IncomeSourceEntity> incomeSources,
            List<SavingsAccountEntity> savingsAccounts,
            List<ConsentRecordEntity> consentRecords,
            List<AuthIdentityEntity> authIdentities,
            List<RefreshTokenEntity> refreshTokens) {
        return DataExportDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .baseCurrency(user.getBaseCurrency())
                .createdAt(user.getCreatedAt())
                .exportedAt(Instant.now())
                .incomeSources(incomeMapper.toDtos(incomeSources))
                .savingsAccounts(savingsMapper.toDtos(savingsAccounts))
                .consentRecords(toConsentDtos(consentRecords))
                .authIdentities(toAuthIdentityDtos(authIdentities))
                .refreshTokens(toRefreshTokenDtos(refreshTokens))
                .build();
    }

    private List<ConsentRecordDto> toConsentDtos(List<ConsentRecordEntity> entities) {
        return entities.stream().map(this::toConsentDto).toList();
    }

    private ConsentRecordDto toConsentDto(ConsentRecordEntity entity) {
        return ConsentRecordDto.builder()
                .consentType(entity.getConsentType())
                .version(entity.getVersion())
                .grantedAt(entity.getGrantedAt())
                .build();
    }

    private List<AuthIdentityDto> toAuthIdentityDtos(List<AuthIdentityEntity> entities) {
        return entities.stream().map(this::toAuthIdentityDto).toList();
    }

    private AuthIdentityDto toAuthIdentityDto(AuthIdentityEntity entity) {
        return AuthIdentityDto.builder()
                .provider(entity.getProvider())
                .providerUid(entity.getProviderUid())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<RefreshTokenDto> toRefreshTokenDtos(List<RefreshTokenEntity> entities) {
        return entities.stream().map(this::toRefreshTokenDto).toList();
    }

    private RefreshTokenDto toRefreshTokenDto(RefreshTokenEntity entity) {
        return RefreshTokenDto.builder()
                .id(entity.getId())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .revoked(entity.isRevoked())
                .build();
    }
}
