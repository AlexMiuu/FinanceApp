package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireFound;
import static com.personalfinance.user.service.RequestGuards.requireUser;

import java.util.UUID;

import com.personalfinance.user.dto.DataExportDto;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.mapper.DataExportMapper;
import com.personalfinance.user.repository.AuthIdentityRepository;
import com.personalfinance.user.repository.ConsentRecordRepository;
import com.personalfinance.user.repository.IncomeSourceRepository;
import com.personalfinance.user.repository.RefreshTokenRepository;
import com.personalfinance.user.repository.SavingsAccountRepository;
import com.personalfinance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Machine-readable export of every personal-data class this service holds
 * (GDPR Art. 20). Distinct from the report CSV export, which is a product
 * feature rather than a data right.
 */
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final UserRepository users;
    private final IncomeSourceRepository incomeSources;
    private final SavingsAccountRepository savingsAccounts;
    private final ConsentRecordRepository consentRecords;
    private final AuthIdentityRepository authIdentities;
    private final RefreshTokenRepository refreshTokens;
    private final DataExportMapper dataExportMapper;

    @Transactional(readOnly = true)
    public DataExportDto exportFor(UUID userId) {
        requireUser(userId);

        UserEntity user = requireFound(users.findById(userId), "Account not found");

        return dataExportMapper.toDto(
                user,
                incomeSources.findByUserIdOrderByCreatedAtAsc(userId),
                savingsAccounts.findByUserIdOrderByNameAsc(userId),
                consentRecords.findByUserIdOrderByGrantedAtAsc(userId),
                authIdentities.findByUser_IdOrderByCreatedAtAsc(userId),
                refreshTokens.findByUserIdOrderByCreatedAtAsc(userId));
    }
}
