package com.personalfinance.user.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import com.personalfinance.user.config.AuthProperties;
import com.personalfinance.user.service.JwtService;
import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.JWTClaimsSet;
import com.personalfinance.user.entity.UserEntity;

class JwtServiceTest {

    /** Ephemeral key pair, 15 minute access tokens — the dev defaults. */
    private static AuthProperties authProperties() {
        return new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), false,
                new AuthProperties.Jwt(""), new AuthProperties.Google("", ""));
    }

    @Test
    void issuedTokenRoundTrips() throws Exception {
        JwtService service = new JwtService(authProperties());
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);

        String token = service.issueAccessToken(user);
        Optional<JWTClaimsSet> claims = service.validate(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get().getStringClaim("email")).isEqualTo("alex@example.com");
        assertThat(claims.get().getIssuer()).isEqualTo(JwtService.ISSUER);
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        JwtService service = new JwtService(authProperties());
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);

        String token = service.issueAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.validate(tampered)).isEmpty();
    }

    @Test
    void tokenFromDifferentKeyIsRejected() throws Exception {
        JwtService issuer = new JwtService(authProperties());
        JwtService verifier = new JwtService(authProperties());
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);

        String token = issuer.issueAccessToken(user);

        assertThat(verifier.validate(token)).isEmpty();
    }
}
