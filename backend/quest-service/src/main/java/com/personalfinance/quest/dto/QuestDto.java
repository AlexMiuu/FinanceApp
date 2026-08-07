package com.personalfinance.quest.dto;

import java.util.Map;
import java.util.UUID;

public record QuestDto(UUID id, String templateCode, String title, String status,
        String kind, long target, long progress, String periodStart, String periodEnd,
        Map<String, Object> params) {
}
