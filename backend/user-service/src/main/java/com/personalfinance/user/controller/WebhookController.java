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

import com.personalfinance.user.dto.WebhookCreateRequestDto;
import com.personalfinance.user.dto.WebhookCreatedDto;
import com.personalfinance.user.dto.WebhookDto;
import com.personalfinance.user.service.WebhookSubscriptionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Webhook subscription management for the signed-in owner (M14/D6).
 *
 * These routes sit under {@code /api/v1/me}, covered by the normal JWT
 * chain — a user registers and revokes their own webhook endpoints with
 * their existing session.
 */
@RestController
@RequestMapping("/api/v1/me/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookSubscriptionService webhookSubscriptionService;

    /** The 201 body is the only time the signing secret is ever readable. */
    @PostMapping
    public ResponseEntity<WebhookCreatedDto> create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody WebhookCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhookSubscriptionService.create(userId, request));
    }

    @GetMapping
    public List<WebhookDto> list(@AuthenticationPrincipal UUID userId) {
        return webhookSubscriptionService.list(userId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        webhookSubscriptionService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
