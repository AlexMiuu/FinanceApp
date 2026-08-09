package com.personalfinance.report.dto;

import java.time.Instant;

public record WeatherStateExportDto(String currentBand, String pendingBand, Instant pendingSince,
        Instant updatedAt) {
}
