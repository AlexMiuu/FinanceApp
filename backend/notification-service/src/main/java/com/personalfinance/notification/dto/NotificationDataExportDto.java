package com.personalfinance.notification.dto;

import java.util.List;

public record NotificationDataExportDto(List<NotificationExportDto> notifications) {
}
