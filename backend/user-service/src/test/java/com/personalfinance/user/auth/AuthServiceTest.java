package com.personalfinance.user.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.personalfinance.user.config.AuthProperties;
import com.personalfinance.user.exception.EmailAlreadyUsedException;
import com.personalfinance.user.exception.InvalidCredentialsException;
import com.personalfinance.user.exception.InvalidRefreshTokenException;
import com.personalfinance.user.service.AuthService;
import com.personalfinance.user.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.personalfinance.user.repository.AuthIdentityRepository;
import com.personalfinance.user.entity.RefreshTokenEntity;
import com.personalfinance.user.repository.RefreshTokenRepository;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.repository.UserRepository;

class AuthServiceTest {

    private final PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    private UserRepository users;
    private AuthIdentityRepository identities;
    private RefreshTokenRepository refreshTokens;
    private AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        users = mock(UserRepository.class);
        identities = mock(AuthIdentityRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        AuthProperties authProperties = new AuthProperties(
                Duration.ofMinutes(15), Duration.ofDays(30), false,
                new AuthProperties.Jwt(""), new AuthProperties.Google("", ""));
        service = new AuthService(users, identities, refreshTokens, encoder,
                new JwtService(authProperties), event -> { }, authProperties);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokens.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmailIgnoreCase("alex@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("alex@example.com", "password123", "Alex"))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        UserEntity user = new UserEntity("alex@example.com", encoder.encode("correct-password"), "Alex", null);
        when(users.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alex@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsOauthOnlyAccount() {
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);
        when(users.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alex@example.com", "any-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginReturnsTokensForValidCredentials() {
        UserEntity user = new UserEntity("alex@example.com", encoder.encode("correct-password"), "Alex", null);
        when(users.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));

        var tokens = service.login("alex@example.com", "correct-password");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void refreshReuseRevokesAllSessions() {
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);
        RefreshTokenEntity revoked = new RefreshTokenEntity(user.getId(), "hash", Instant.now().plusSeconds(3600));
        revoked.revoke();
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("reused-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokens).revokeAllForUser(user.getId());
    }
}
