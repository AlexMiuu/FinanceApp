package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireBody;
import static com.personalfinance.user.service.RequestGuards.requireFound;
import static com.personalfinance.user.service.RequestGuards.requireId;
import static com.personalfinance.user.service.RequestGuards.requireUser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.user.dto.PersonalAccessTokenCreatedDto;
import com.personalfinance.user.dto.PersonalAccessTokenDto;
import com.personalfinance.user.dto.PersonalAccessTokenRequestDto;
import com.personalfinance.user.dto.TokenIntrospectionDtos;
import com.personalfinance.user.entity.PersonalAccessTokenEntity;
import com.personalfinance.user.mapper.PersonalAccessTokenMapper;
import com.personalfinance.user.repository.PersonalAccessTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Issues, lists, revokes, and introspects personal access tokens (M14).
 *
 * The security properties this class is responsible for:
 * <ul>
 *   <li>The raw token is generated here, returned once, and never stored — only
 *       its SHA-256 digest reaches the database.</li>
 *   <li>No log statement in this class interpolates the token or its hash
 *       (NFR-4). Events are logged by token id instead.</li>
 *   <li>Revocation is a column read on the introspection path, not a cached
 *       decision, so it takes effect on the next request with no propagation
 *       delay.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PersonalAccessTokenService {

    /** Distinguishes an Argali token at a glance in a config file or a leak report. */
    static final String TOKEN_PREFIX = "pat_";

    /** 256 bits of entropy — the same order as the SHA-256 digest it is stored under. */
    private static final int TOKEN_BYTES = 32;

    /** The only scope M14 issues; the column is a string so adding more needs no migration. */
    public static final String SCOPE_READ = "read";

    private static final Logger log = LoggerFactory.getLogger(PersonalAccessTokenService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PersonalAccessTokenRepository tokens;
    private final PersonalAccessTokenMapper personalAccessTokenMapper;
    private final JwtService jwtService;

    @Transactional
    public PersonalAccessTokenCreatedDto create(UUID userId, PersonalAccessTokenRequestDto request) {
        requireUser(userId);
        requireBody(request);
        requireName(request);

        String rawToken = generateToken();
        PersonalAccessTokenEntity entity = tokens.save(new PersonalAccessTokenEntity(
                userId, request.getName().trim(), sha256Hex(rawToken), SCOPE_READ));

        log.info("Issued personal access token {} for user {}", entity.getId(), userId);
        return personalAccessTokenMapper.toCreatedDto(entity, rawToken);
    }

    @Transactional(readOnly = true)
    public List<PersonalAccessTokenDto> listFor(UUID userId) {
        requireUser(userId);

        return tokens.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(personalAccessTokenMapper::toDto)
                .toList();
    }

    /**
     * Revokes one of the caller's own tokens. A token belonging to somebody else
     * is a 404 rather than a 403: the caller has no legitimate way to know it
     * exists, and saying so would confirm an id for them.
     */
    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        requireUser(userId);
        requireId(tokenId, "Token");

        PersonalAccessTokenEntity entity = requireFound(
                tokens.findByIdAndUserId(tokenId, userId), "Token not found");

        entity.revoke();
        tokens.save(entity);
        log.info("Revoked personal access token {} for user {}", tokenId, userId);
    }

    /**
     * The gateway's token-exchange call: resolve a raw token to a short-lived
     * JWT the downstream services already know how to validate.
     *
     * Every negative outcome — malformed, unknown, revoked — returns the same
     * inactive response, so a caller cannot tell them apart by probing.
     */
    @Transactional
    public TokenIntrospectionDtos.Response introspect(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenIntrospectionDtos.Response.inactive();
        }

        Optional<PersonalAccessTokenEntity> found = tokens.findByTokenHash(sha256Hex(rawToken));
        if (found.isEmpty()) {
            return TokenIntrospectionDtos.Response.inactive();
        }

        PersonalAccessTokenEntity entity = found.get();
        if (entity.isRevoked()) {
            log.debug("Rejected revoked personal access token {}", entity.getId());
            return TokenIntrospectionDtos.Response.inactive();
        }

        entity.markUsed();
        tokens.save(entity);

        return TokenIntrospectionDtos.Response.active(
                jwtService.issueTokenExchangeToken(entity.getUserId(), entity.getScope()),
                entity.getScope());
    }

    private static void requireName(PersonalAccessTokenRequestDto request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token name is required");
        }
    }

    private static String generateToken() {
        byte[] entropy = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(entropy);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to store access tokens", e);
        }
    }
}
