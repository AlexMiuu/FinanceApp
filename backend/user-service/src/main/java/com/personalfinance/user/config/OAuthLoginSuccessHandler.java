package com.personalfinance.user.config;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.personalfinance.user.auth.AuthService;
import com.personalfinance.user.auth.AuthService.TokenPair;
import com.personalfinance.user.auth.RefreshCookies;
import com.personalfinance.user.domain.UserEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Completes a Google login: links or creates the local account, then hands the
 * browser back to the SPA with a refresh cookie set. The SPA bootstraps the
 * session via POST /api/v1/auth/refresh — no tokens ever appear in URLs.
 */
@Component
public class OAuthLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final RefreshCookies cookies;

    public OAuthLoginSuccessHandler(AuthService authService, RefreshCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String email = principal.getAttribute("email");
        Boolean emailVerified = principal.getAttribute("email_verified");

        if (email == null || !Boolean.TRUE.equals(emailVerified)) {
            getRedirectStrategy().sendRedirect(request, response, "/?login=error");
            return;
        }

        UserEntity user = authService.findOrCreateGoogleUser(
                principal.getAttribute("sub"),
                email,
                principal.getAttribute("name"),
                principal.getAttribute("picture"));
        TokenPair tokens = authService.issueTokens(user);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookies.create(tokens.refreshToken(), tokens.refreshTtl()).toString());
        getRedirectStrategy().sendRedirect(request, response, "/?login=ok");
    }
}
