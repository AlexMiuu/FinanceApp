package com.personalfinance.expense.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Preconditions shared by the service layer. Every check maps to the HTTP
 * status the client should see, so a service can fail fast without each method
 * repeating the same null/ownership plumbing.
 */
public final class RequestGuards {

    private RequestGuards() {
    }

    /** Null means the resource server never populated a principal. */
    public static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }

    public static UUID requireId(UUID id, String what) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " id is required");
        }
        return id;
    }

    /**
     * Unboxes a money amount only once it is known to be present and positive,
     * so the service never depends on the controller having run @Valid.
     */
    public static long requireAmount(Long amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        return amount;
    }

    public static <T> T requireBody(T body) {
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return body;
    }

    public static <T> T requireFound(Optional<T> value, String message) {
        return value.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }
}
