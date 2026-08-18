package com.personalfinance.user.controller;

import java.util.Map;

import com.personalfinance.user.auth.RefreshCookies;
import com.personalfinance.user.config.AuthProperties;
import com.personalfinance.user.dto.AuthDtos;
import com.personalfinance.user.exception.InvalidRefreshTokenException;
import com.personalfinance.user.service.AuthService;
import com.personalfinance.user.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.user.service.AuthService.TokenPair;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshCookies cookies;
    private final AuthProperties authProperties;

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        // Enforced here rather than only hidden in the UI: a closed deployment must
        // stay closed against anyone posting straight at the endpoint.
        if (!authProperties.registrationEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This Argali is not open for new accounts.");
        }
        TokenPair tokens = authService.register(request.email(), request.password(), request.displayName());
        return withRefreshCookie(HttpStatus.CREATED, tokens);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        TokenPair tokens = authService.login(request.email(), request.password());
        return withRefreshCookie(HttpStatus.OK, tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(
            @CookieValue(name = RefreshCookies.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        TokenPair tokens = authService.refresh(refreshToken);
        return withRefreshCookie(HttpStatus.OK, tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expired().toString())
                .build();
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return jwtService.jwks();
    }

    /** Which ways in are available, so the sign-in screen only offers real ones. */
    @GetMapping("/oauth/providers")
    public Map<String, Boolean> providers() {
        String clientId = authProperties.google().clientId();
        return Map.of(
                "google", clientId != null && !clientId.isBlank(),
                "registration", authProperties.registrationEnabled());
    }

    private ResponseEntity<AuthDtos.AuthResponse> withRefreshCookie(HttpStatus status, TokenPair tokens) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken(), tokens.refreshTtl()).toString())
                .body(AuthDtos.AuthResponse.of(tokens));
    }
}
