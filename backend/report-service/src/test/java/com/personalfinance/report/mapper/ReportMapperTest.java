package com.personalfinance.report.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.report.dto.ReportDto;
import com.personalfinance.report.entity.ReportEntity;

class ReportMapperTest {

    private final ReportMapper mapper = new ReportMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void copiesEveryExposedField() {
        ReportEntity entity = new ReportEntity(userId, "Groceries", Map.of("from", "2026-07-01"));
        entity.recordRun(Map.of("totalSpent", 5000L));

        ReportDto dto = mapper.toDto(entity);

        assertThat(dto.id()).isEqualTo(entity.getId());
        assertThat(dto.name()).isEqualTo("Groceries");
        assertThat(dto.filters()).isEqualTo(entity.getFilters());
        assertThat(dto.lastRunAt()).isEqualTo(entity.getLastRunAt());
        assertThat(dto.cachedResult()).isEqualTo(entity.getCachedResult());
    }

    @Test
    void mapsAnEmptyListToAnEmptyList() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
    }

    @Test
    void aReportThatHasNeverBeenRunHasNullLastRunAndCachedResult() {
        ReportEntity entity = new ReportEntity(userId, "Never run", Map.of());

        ReportDto dto = mapper.toDto(entity);

        assertThat(dto.lastRunAt()).isNull();
        assertThat(dto.cachedResult()).isNull();
    }
}
