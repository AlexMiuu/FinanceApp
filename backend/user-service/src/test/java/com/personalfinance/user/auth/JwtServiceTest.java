package com.personalfinance.user.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nimbusds.jwt.JWTClaimsSet;
import com.personalfinance.user.domain.UserEntity;

class JwtServiceTest {

    @Test
    void issuedTokenRoundTrips() throws Exception {
        JwtService service = new JwtService("", Duration.ofMinutes(15));
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
        JwtService service = new JwtService("", Duration.ofMinutes(15));
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);

        String token = service.issueAccessToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.validate(tampered)).isEmpty();
    }

    @Test
    void tokenFromDifferentKeyIsRejected() throws Exception {
        JwtService issuer = new JwtService("", Duration.ofMinutes(15));
        JwtService verifier = new JwtService("", Duration.ofMinutes(15));
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);

        String token = issuer.issueAccessToken(user);

        assertThat(verifier.validate(token)).isEmpty();
    }
}
