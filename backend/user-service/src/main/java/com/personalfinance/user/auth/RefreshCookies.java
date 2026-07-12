package com.personalfinance.user.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token travels only in an HttpOnly cookie scoped to the auth
 * endpoints, so it is invisible to frontend JavaScript and never attached to
 * ordinary API calls.
 */
@Component
public class RefreshCookies {

    public static final String COOKIE_NAME = "refresh_token";

    private final boolean secure;

    public RefreshCookies(@Value("${auth.cookie-secure:false}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie create(String value, Duration ttl) {
        return builder(value).maxAge(ttl).build();
    }

    public ResponseCookie expired() {
        return builder("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/v1/auth");
    }
}
