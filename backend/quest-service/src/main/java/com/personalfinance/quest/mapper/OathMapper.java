package com.personalfinance.quest.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.quest.dto.OathDto;
import com.personalfinance.quest.dto.OathExportDto;
import com.personalfinance.quest.entity.OathEntity;

@Component
public class OathMapper {

    public OathDto toDto(OathEntity entity) {
        return new OathDto(entity.getId(), entity.getCategoryId(), entity.getCategoryName(),
                entity.getPledgedAmount(), entity.getCreatedAt(), entity.getExpiresAt(),
                entity.getStatus(), entity.getResolvedAt(), entity.getMatchedExpenseId());
    }

    public List<OathDto> toDtos(List<OathEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }

    public OathExportDto toExportDto(OathEntity entity) {
        return new OathExportDto(entity.getId(), entity.getCategoryId(), entity.getCategoryName(),
                entity.getPledgedAmount(), entity.getStatus(), entity.getMatchedExpenseId(),
                entity.getExpiresAt(), entity.getResolvedAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public List<OathExportDto> toExportDtos(List<OathEntity> entities) {
        return entities.stream().map(this::toExportDto).toList();
    }
}
