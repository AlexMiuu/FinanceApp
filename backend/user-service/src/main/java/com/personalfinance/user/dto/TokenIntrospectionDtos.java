package com.personalfinance.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;

/**
 * The service-to-service token-exchange contract used by the gateway.
 *
 * The token travels in the request body, never in the URL: a query parameter
 * would land in access logs and proxy history (NFR-4).
 */
public final class TokenIntrospectionDtos {

    private TokenIntrospectionDtos() {
    }

    public record Request(@NotBlank(message = "token is required") String token) {
    }

    /**
     * Mirrors the shape Spring Security's opaque-token support expects. An
     * unknown, malformed, or revoked token is {@code active=false} with a 200 —
     * a negative lookup is a normal result, not a server error.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(boolean active, String accessToken, String scope) {

        private static final Response INACTIVE = new Response(false, null, null);

        public static Response inactive() {
            return INACTIVE;
        }

        public static Response active(String accessToken, String scope) {
            return new Response(true, accessToken, scope);
        }
    }
}
