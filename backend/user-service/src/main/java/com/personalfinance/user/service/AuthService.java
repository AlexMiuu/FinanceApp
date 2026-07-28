package com.personalfinance.user.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import com.personalfinance.user.exception.EmailAlreadyUsedException;
import com.personalfinance.user.exception.InvalidCredentialsException;
import com.personalfinance.user.exception.InvalidRefreshTokenException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.user.events.UserRegisteredEvent;

import com.personalfinance.user.entity.AuthIdentityEntity;
import com.personalfinance.user.repository.AuthIdentityRepository;
import com.personalfinance.user.entity.RefreshTokenEntity;
import com.personalfinance.user.repository.RefreshTokenRepository;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.repository.UserRepository;

@Service
@AllArgsConstructor
public class AuthService {

    public record TokenPair(String accessToken, String refreshToken, Duration refreshTtl, UserEntity user) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final AuthIdentityRepository identities;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration refreshTokenTtl;

    @Transactional
    public TokenPair register(String email, String password, String displayName) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException();
        }
        UserEntity user = new UserEntity(email, passwordEncoder.encode(password), displayName, null);
        users.save(user);
        identities.save(new AuthIdentityEntity(user, AuthIdentityEntity.PROVIDER_PASSWORD, user.getId().toString()));
        eventPublisher.publishEvent(
                new UserRegisteredEvent(user.getId(), user.getEmail(), user.getDisplayName(), Instant.now()));
        return issueTokens(user);
    }

    @Transactional
    public TokenPair login(String email, String password) {
        UserEntity user = users.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /**
     * Finds the account for a Google login, linking by verified email when the
     * user first registered with a password, or creating a fresh account.
     */
    @Transactional
    public UserEntity findOrCreateGoogleUser(String googleSub, String email, String name, String avatarUrl) {
        Optional<AuthIdentityEntity> identity =
                identities.findByProviderAndProviderUid(AuthIdentityEntity.PROVIDER_GOOGLE, googleSub);
        if (identity.isPresent()) {
            return identity.get().getUser();
        }
        UserEntity user = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            UserEntity created = new UserEntity(email, null, name != null ? name : email, avatarUrl);
            users.save(created);
            eventPublisher.publishEvent(new UserRegisteredEvent(
                    created.getId(), created.getEmail(), created.getDisplayName(), Instant.now()));
            return created;
        });
        if (user.getAvatarUrl() == null && avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }
        identities.save(new AuthIdentityEntity(user, AuthIdentityEntity.PROVIDER_GOOGLE, googleSub));
        return user;
    }

    @Transactional
    public TokenPair issueTokens(UserEntity user) {
        String raw = newOpaqueToken();
        refreshTokens.save(new RefreshTokenEntity(user.getId(), sha256(raw), Instant.now().plus(refreshTokenTtl)));
        return new TokenPair(jwtService.issueAccessToken(user), raw, refreshTokenTtl, user);
    }

    /**
     * Rotates the refresh token. Reuse of an already-rotated token is treated
     * as theft: every session for that user is revoked.
     */
    @Transactional
    public TokenPair refresh(String rawToken) {
        RefreshTokenEntity token = refreshTokens.findByTokenHash(sha256(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.isRevoked()) {
            refreshTokens.revokeAllForUser(token.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }
        token.revoke();
        UserEntity user = users.findById(token.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(sha256(rawToken)).ifPresent(RefreshTokenEntity::revoke);
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
