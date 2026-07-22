package com.personalfinance.notification.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.notification.domain.NotificationEntity;
import com.personalfinance.notification.domain.NotificationRepository;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    public record NotificationDto(UUID id, String type, String title, String body,
            Instant createdAt, boolean read) {

        public static NotificationDto of(NotificationEntity n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(),
                    n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    public record NotificationList(List<NotificationDto> items, long unread) {
    }

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationList list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = userId(jwt);
        return new NotificationList(
                notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(NotificationDto::of).toList(),
                notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public Map<String, Boolean> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        NotificationEntity notification = notifications.findByIdAndUserId(id, userId(jwt))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.markRead();
        return Map.of("read", true);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Boolean> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notifications.findByUserIdAndReadAtIsNull(userId(jwt)).forEach(NotificationEntity::markRead);
        return Map.of("read", true);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
