package com.personalfinance.report.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ReportDto(UUID id, String name, Map<String, Object> filters, Instant lastRunAt,
        Map<String, Object> cachedResult) {
}
