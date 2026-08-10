package com.personalfinance.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.user.dto.PersonalAccessTokenCreatedDto;
import com.personalfinance.user.dto.PersonalAccessTokenDto;
import com.personalfinance.user.dto.PersonalAccessTokenRequestDto;
import com.personalfinance.user.service.PersonalAccessTokenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Token management for the signed-in owner (M14).
 *
 * These routes sit under {@code /api/v1/me}, so they are covered by the normal
 * JWT chain — a user mints and revokes API tokens with their existing session,
 * and a personal access token can never be used to mint another one.
 */
@RestController
@RequestMapping("/api/v1/me/tokens")
@RequiredArgsConstructor
public class PersonalAccessTokenController {

    private final PersonalAccessTokenService personalAccessTokenService;

    /** The 201 body is the only time the raw token is ever readable. */
    @PostMapping
    public ResponseEntity<PersonalAccessTokenCreatedDto> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PersonalAccessTokenRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personalAccessTokenService.create(userId, request));
    }

    @GetMapping
    public List<PersonalAccessTokenDto> list(@AuthenticationPrincipal UUID userId) {
        return personalAccessTokenService.listFor(userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        personalAccessTokenService.revoke(userId, id);
        return ResponseEntity.noContent().build();
    }
}
