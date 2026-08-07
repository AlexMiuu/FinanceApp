package com.personalfinance.notification.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.notification.dto.NotificationDataExportDto;
import com.personalfinance.notification.dto.NotificationListDto;
import com.personalfinance.notification.service.NotificationService;
import com.personalfinance.notification.service.PrivacyService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;
    private final PrivacyService privacyService;

    public NotificationController(NotificationService service, PrivacyService privacyService) {
        this.service = service;
        this.privacyService = privacyService;
    }

    @GetMapping
    public NotificationListDto list(@AuthenticationPrincipal Jwt jwt) {
        return service.listFor(userId(jwt));
    }

    @PostMapping("/{id}/read")
    public Map<String, Boolean> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.markRead(id, userId(jwt));
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    public Map<String, Boolean> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(userId(jwt));
        return Map.of("read", true);
    }

    /** GDPR Art. 20 machine-readable export of this service's data classes (M9, per docs/records-of-processing.md). */
    @GetMapping("/export/me")
    public NotificationDataExportDto exportMyData(@AuthenticationPrincipal Jwt jwt) {
        return privacyService.exportUserData(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
