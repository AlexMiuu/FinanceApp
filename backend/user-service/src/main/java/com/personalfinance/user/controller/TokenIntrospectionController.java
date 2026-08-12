package com.personalfinance.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.user.dto.TokenIntrospectionDtos;
import com.personalfinance.user.service.PersonalAccessTokenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The gateway's token-exchange endpoint (M14).
 *
 * Lives under {@code /api/v1/auth}, which {@code SecurityConfig} marks
 * {@code permitAll} — by definition the caller has no JWT yet; the personal
 * access token in the body is the credential being presented.
 *
 * Kept apart from {@link AuthController} because this is a service-to-service
 * route, not a browser-facing one: nothing here sets cookies or touches a
 * session.
 *
 * Always answers 200. An unknown, malformed, or revoked token is
 * {@code {"active": false}}, deliberately indistinguishable so a caller cannot
 * learn why a token failed by probing.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class TokenIntrospectionController {

    private final PersonalAccessTokenService personalAccessTokenService;

    @PostMapping("/introspect")
    public TokenIntrospectionDtos.Response introspect(
            @Valid @RequestBody TokenIntrospectionDtos.Request request) {
        return personalAccessTokenService.introspect(request.token());
    }
}
