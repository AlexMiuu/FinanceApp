package com.personalfinance.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.user.dto.PersonalAccessTokenCreatedDto;
import com.personalfinance.user.dto.PersonalAccessTokenRequestDto;
import com.personalfinance.user.dto.TokenIntrospectionDtos;
import com.personalfinance.user.entity.PersonalAccessTokenEntity;
import com.personalfinance.user.mapper.PersonalAccessTokenMapper;
import com.personalfinance.user.repository.PersonalAccessTokenRepository;

/**
 * Given-When-Then coverage of the M14 exit criteria this class is directly
 * responsible for: revocation takes effect immediately on the introspection
 * path, and no negative outcome (unknown/malformed/revoked) is distinguishable
 * from another.
 */
class PersonalAccessTokenServiceTest {

    private final UUID userId = UUID.randomUUID();

    private PersonalAccessTokenRepository tokens;
    private JwtService jwtService;
    private PersonalAccessTokenService service;

    @BeforeEach
    void setUp() {
        tokens = mock(PersonalAccessTokenRepository.class);
        jwtService = mock(JwtService.class);
        service = new PersonalAccessTokenService(tokens, new PersonalAccessTokenMapper(), jwtService);
        when(tokens.save(any(PersonalAccessTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createRejectsABlankName() {
        PersonalAccessTokenRequestDto request = new PersonalAccessTokenRequestDto();
        request.setName("   ");

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createReturnsTheRawTokenExactlyOnceAndStoresOnlyItsHash() {
        PersonalAccessTokenRequestDto request = new PersonalAccessTokenRequestDto();
        request.setName("my script");

        PersonalAccessTokenCreatedDto created = service.create(userId, request);

        assertThat(created.getToken()).startsWith("pat_");

        ArgumentCaptor<PersonalAccessTokenEntity> saved = ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(PersonalAccessTokenService.sha256Hex(created.getToken()))
                .doesNotContain(created.getToken());
    }

    @Test
    void introspectReturnsInactiveForAnUnknownToken() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        TokenIntrospectionDtos.Response response = service.introspect("pat_doesnotexist");

        assertThat(response.active()).isFalse();
        assertThat(response.accessToken()).isNull();
    }

    @Test
    void introspectReturnsInactiveForABlankToken() {
        TokenIntrospectionDtos.Response response = service.introspect("   ");

        assertThat(response.active()).isFalse();
    }

    @Test
    void introspectReturnsInactiveForANullToken() {
        TokenIntrospectionDtos.Response response = service.introspect(null);

        assertThat(response.active()).isFalse();
    }

    @Test
    void revocationTakesEffectImmediatelyOnTheNextIntrospection() {
        String rawToken = "pat_abc123";
        PersonalAccessTokenEntity entity =
                new PersonalAccessTokenEntity(userId, "my script", PersonalAccessTokenService.sha256Hex(rawToken),
                        PersonalAccessTokenService.SCOPE_READ);
        when(tokens.findByTokenHash(PersonalAccessTokenService.sha256Hex(rawToken)))
                .thenReturn(Optional.of(entity));
        when(jwtService.issueTokenExchangeToken(userId, PersonalAccessTokenService.SCOPE_READ))
                .thenReturn("exchanged.jwt.token");

        // Before revocation: active.
        assertThat(service.introspect(rawToken).active()).isTrue();

        // Revoke — no cache, no propagation delay to wait out.
        entity.revoke();

        TokenIntrospectionDtos.Response afterRevocation = service.introspect(rawToken);
        assertThat(afterRevocation.active()).isFalse();
        assertThat(afterRevocation.accessToken()).isNull();
    }

    @Test
    void introspectOnASuccessfulLookupExchangesForAShortLivedJwtAndRecordsLastUsed() {
        String rawToken = "pat_abc123";
        PersonalAccessTokenEntity entity =
                new PersonalAccessTokenEntity(userId, "my script", PersonalAccessTokenService.sha256Hex(rawToken),
                        PersonalAccessTokenService.SCOPE_READ);
        when(tokens.findByTokenHash(PersonalAccessTokenService.sha256Hex(rawToken)))
                .thenReturn(Optional.of(entity));
        when(jwtService.issueTokenExchangeToken(userId, PersonalAccessTokenService.SCOPE_READ))
                .thenReturn("exchanged.jwt.token");

        TokenIntrospectionDtos.Response response = service.introspect(rawToken);

        assertThat(response.active()).isTrue();
        assertThat(response.accessToken()).isEqualTo("exchanged.jwt.token");
        assertThat(response.scope()).isEqualTo(PersonalAccessTokenService.SCOPE_READ);
        assertThat(entity.getLastUsedAt()).isNotNull();
        verify(tokens).save(entity);
    }

    @Test
    void revokeIsIdempotentAndKeepsTheOriginalTimestamp() {
        PersonalAccessTokenEntity entity = new PersonalAccessTokenEntity(userId, "my script", "hash",
                PersonalAccessTokenService.SCOPE_READ);
        when(tokens.findByIdAndUserId(any(), any())).thenReturn(Optional.of(entity));

        service.revoke(userId, entity.getId());
        var firstRevokedAt = entity.getRevokedAt();
        service.revoke(userId, entity.getId());

        assertThat(entity.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    @Test
    void revokingAnotherUsersTokenIsNotFoundNotForbidden() {
        when(tokens.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(userId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listRejectsANullUserId() {
        assertThatThrownBy(() -> service.listFor(null)).isInstanceOf(Exception.class);
    }
}
