package com.personalfinance.report.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.report.dto.ReportDto;
import com.personalfinance.report.entity.ReportEntity;

/** Entity -> DTO translation for saved reports. */
@Component
public class ReportMapper {

    public ReportDto toDto(ReportEntity entity) {
        return new ReportDto(entity.getId(), entity.getName(), entity.getFilters(), entity.getLastRunAt(),
                entity.getCachedResult());
    }

    public List<ReportDto> toDtos(List<ReportEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
