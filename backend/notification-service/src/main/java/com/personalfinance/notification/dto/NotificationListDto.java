package com.personalfinance.notification.dto;

import java.util.List;

public record NotificationListDto(List<NotificationDto> items, long unread) {
}
