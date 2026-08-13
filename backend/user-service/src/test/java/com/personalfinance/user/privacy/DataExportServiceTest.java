package com.personalfinance.user.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.user.dto.DataExportDto;
import com.personalfinance.user.entity.AuthIdentityEntity;
import com.personalfinance.user.entity.DashboardLayoutEntity;
import com.personalfinance.user.entity.ConsentRecordEntity;
import com.personalfinance.user.entity.IncomeSourceEntity;
import com.personalfinance.user.entity.RefreshTokenEntity;
import com.personalfinance.user.entity.SavingsAccountEntity;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.mapper.DashboardLayoutMapper;
import com.personalfinance.user.mapper.DataExportMapper;
import com.personalfinance.user.mapper.IncomeMapper;
import com.personalfinance.user.mapper.SavingsMapper;
import com.personalfinance.user.repository.AuthIdentityRepository;
import com.personalfinance.user.repository.ConsentRecordRepository;
import com.personalfinance.user.repository.DashboardLayoutRepository;
import com.personalfinance.user.repository.IncomeSourceRepository;
import com.personalfinance.user.repository.RefreshTokenRepository;
import com.personalfinance.user.repository.SavingsAccountRepository;
import com.personalfinance.user.repository.UserRepository;
import com.personalfinance.user.service.DashboardLayoutService;
import com.personalfinance.user.service.DataExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DataExportServiceTest {

    private UserRepository users;
    private IncomeSourceRepository incomeSources;
    private SavingsAccountRepository savingsAccounts;
    private ConsentRecordRepository consentRecords;
    private AuthIdentityRepository authIdentities;
    private RefreshTokenRepository refreshTokens;
    private DashboardLayoutRepository dashboardLayouts;
    private DataExportService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        incomeSources = mock(IncomeSourceRepository.class);
        savingsAccounts = mock(SavingsAccountRepository.class);
        consentRecords = mock(ConsentRecordRepository.class);
        authIdentities = mock(AuthIdentityRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        dashboardLayouts = mock(DashboardLayoutRepository.class);
        service = new DataExportService(users, incomeSources, savingsAccounts, consentRecords,
                authIdentities, refreshTokens,
                new DashboardLayoutService(dashboardLayouts,
                        new DashboardLayoutMapper(new ObjectMapper())),
                new DataExportMapper(new IncomeMapper(), new SavingsMapper()));
    }

    @Test
    void exportComposesEveryDataClassThisServiceHolds() {
        UserEntity user = new UserEntity("alex@example.com", "hash", "Alex", "https://avatar");
        UUID userId = user.getId();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(incomeSources.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                IncomeSourceEntity.builder()
                        .userId(userId).name("Salariu").amount(1_000_000).recurrence("MONTHLY")
                        .startDate(LocalDate.of(2026, 1, 1)).build()));
        when(savingsAccounts.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(
                new SavingsAccountEntity(userId, "Economii", 250_000)));
        when(consentRecords.findByUserIdOrderByGrantedAtAsc(userId)).thenReturn(List.of(
                new ConsentRecordEntity(userId, ConsentRecordEntity.TYPE_TOS_PRIVACY,
                        "2026-08-06", Instant.parse("2026-08-06T10:00:00Z"))));
        when(authIdentities.findByUser_IdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                new AuthIdentityEntity(user, AuthIdentityEntity.PROVIDER_GOOGLE, "google-sub-123")));
        when(refreshTokens.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                new RefreshTokenEntity(userId, "hashed-token", Instant.parse("2026-09-05T10:00:00Z"))));

        DataExportDto export = service.exportFor(userId);

        assertThat(export.getUserId()).isEqualTo(userId);
        assertThat(export.getEmail()).isEqualTo("alex@example.com");
        assertThat(export.getDisplayName()).isEqualTo("Alex");
        assertThat(export.getAvatarUrl()).isEqualTo("https://avatar");
        assertThat(export.getBaseCurrency()).isEqualTo("RON");
        assertThat(export.getExportedAt()).isNotNull();

        assertThat(export.getIncomeSources()).singleElement()
                .satisfies(income -> {
                    assertThat(income.getName()).isEqualTo("Salariu");
                    assertThat(income.getAmount()).isEqualTo(1_000_000);
                });
        assertThat(export.getSavingsAccounts()).singleElement()
                .satisfies(savings -> {
                    assertThat(savings.getName()).isEqualTo("Economii");
                    assertThat(savings.getBalance()).isEqualTo(250_000);
                });
        assertThat(export.getConsentRecords()).singleElement()
                .satisfies(consent -> {
                    assertThat(consent.getConsentType()).isEqualTo(ConsentRecordEntity.TYPE_TOS_PRIVACY);
                    assertThat(consent.getVersion()).isEqualTo("2026-08-06");
                    assertThat(consent.getGrantedAt()).isEqualTo(Instant.parse("2026-08-06T10:00:00Z"));
                });
        assertThat(export.getAuthIdentities()).singleElement()
                .satisfies(identity -> {
                    assertThat(identity.getProvider()).isEqualTo(AuthIdentityEntity.PROVIDER_GOOGLE);
                    assertThat(identity.getProviderUid()).isEqualTo("google-sub-123");
                });
        assertThat(export.getRefreshTokens()).singleElement()
                .satisfies(token -> {
                    assertThat(token.getExpiresAt()).isEqualTo(Instant.parse("2026-09-05T10:00:00Z"));
                    assertThat(token.isRevoked()).isFalse();
                });
        assertThat(export.getDashboardLayout()).isNotNull();
    }

    @Test
    void exportCarriesTheSavedDashboardArrangement() {
        UserEntity user = new UserEntity("alex@example.com", "hash", "Alex", null);
        UUID userId = user.getId();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(dashboardLayouts.findById(userId)).thenReturn(Optional.of(new DashboardLayoutEntity(
                userId, "{\"main\":[\"ledger\"],\"side\":[\"quests\",\"savings\"]}")));

        DataExportDto export = service.exportFor(userId);

        assertThat(export.getDashboardLayout().getMain()).containsExactly("ledger");
        assertThat(export.getDashboardLayout().getSide()).containsExactly("quests", "savings");
    }

    @Test
    void exportFallsBackToTheDefaultArrangementWhenTheUserNeverRearrangedAnything() {
        UserEntity user = new UserEntity("alex@example.com", "hash", "Alex", null);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        DataExportDto export = service.exportFor(user.getId());

        assertThat(export.getDashboardLayout().getMain()).containsExactly("ledger");
        assertThat(export.getDashboardLayout().getSide()).containsExactly("savings", "quests");
        assertThat(export.getDashboardLayout().getUpdatedAt()).isNull();
    }

    @Test
    void exportNeverLeaksTheRefreshTokenHash() {
        UserEntity user = new UserEntity("alex@example.com", "hash", "Alex", null);
        UUID userId = user.getId();
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokens.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(
                new RefreshTokenEntity(userId, "super-secret-hash", Instant.parse("2026-09-05T10:00:00Z"))));

        DataExportDto export = service.exportFor(userId);

        assertThat(export.toString()).doesNotContain("super-secret-hash");
    }

    @Test
    void exportNeverLeaksThePasswordHash() {
        UserEntity user = new UserEntity("alex@example.com", "argon2-hash", "Alex", null);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        DataExportDto export = service.exportFor(user.getId());

        assertThat(export.toString()).doesNotContain("argon2-hash");
    }

    @Test
    void exportReturnsEmptyCollectionsForAUserWithNoFinancialData() {
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        DataExportDto export = service.exportFor(user.getId());

        assertThat(export.getIncomeSources()).isEmpty();
        assertThat(export.getSavingsAccounts()).isEmpty();
        assertThat(export.getConsentRecords()).isEmpty();
        assertThat(export.getAuthIdentities()).isEmpty();
        assertThat(export.getRefreshTokens()).isEmpty();
    }

    @Test
    void exportRejectsAnUnknownUser() {
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportFor(unknown))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void exportRejectsAMissingPrincipal() {
        assertThatThrownBy(() -> service.exportFor(null))
                .isInstanceOf(ResponseStatusException.class);
    }
}
