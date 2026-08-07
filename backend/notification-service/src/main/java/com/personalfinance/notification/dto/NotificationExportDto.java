package com.personalfinance.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Every column of {@code notifications} except {@code user_id}, which is the
 * subject of the export rather than part of it. Wider than
 * {@link NotificationDto} because GDPR Art. 20 covers the payload and the
 * read timestamp too, neither of which the list endpoint returns.
 */
public record NotificationExportDto(UUID id, String type, String title, String body, Map<String, Object> data,
        Instant readAt, Instant createdAt) {
}
