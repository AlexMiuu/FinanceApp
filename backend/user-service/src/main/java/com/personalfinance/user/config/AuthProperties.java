package com.personalfinance.user.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Everything under {@code auth.*} in application.yml, bound once and injected
 * as a typed bean. Constructor binding also means Lombok-generated constructors
 * on the consuming beans stay valid: they take this bean, not a bare Duration.
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("false") boolean cookieSecure,
        @DefaultValue Jwt jwt,
        @DefaultValue Google google) {

    /** PKCS#8 PEM; empty means "generate an ephemeral key pair" (dev only). */
    public record Jwt(@DefaultValue("") String privateKeyPem) {
    }

    /** Google login is optional: empty clientId disables the whole oauth2Login chain. */
    public record Google(@DefaultValue("") String clientId, @DefaultValue("") String clientSecret) {
    }
}
