package com.personalfinance.user.mapper;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.user.dto.DashboardLayoutDto;
import com.personalfinance.user.dto.DashboardLayoutRequestDto;

import lombok.RequiredArgsConstructor;

/**
 * Translates between the layout's persisted JSON document and its DTOs. The
 * request DTO doubles as the stored shape, so the column and the wire format
 * cannot drift apart.
 */
@Component
@RequiredArgsConstructor
public class DashboardLayoutMapper {

    private final ObjectMapper objectMapper;

    public String toJson(DashboardLayoutRequestDto layout) {
        try {
            return objectMapper.writeValueAsString(layout);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not serialize dashboard layout", e);
        }
    }

    /**
     * Returns empty columns for a document this version cannot read, letting the
     * caller reconcile it back to a full layout rather than failing the request:
     * an unreadable preference is worth less than a dashboard that still renders.
     */
    public DashboardLayoutRequestDto fromJson(String json) {
        try {
            return objectMapper.readValue(json, DashboardLayoutRequestDto.class);
        } catch (JsonProcessingException e) {
            return new DashboardLayoutRequestDto(List.of(), List.of());
        }
    }

    public DashboardLayoutDto toDto(DashboardLayoutRequestDto layout, Instant updatedAt) {
        return DashboardLayoutDto.builder()
                .main(List.copyOf(layout.getMain()))
                .side(List.copyOf(layout.getSide()))
                .updatedAt(updatedAt)
                .build();
    }
}
