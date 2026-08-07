package com.personalfinance.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(UUID id, String type, String title, String body, Instant createdAt, boolean read) {
}
