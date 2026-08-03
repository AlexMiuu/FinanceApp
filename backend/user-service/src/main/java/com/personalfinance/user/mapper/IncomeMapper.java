package com.personalfinance.user.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.user.dto.IncomeDto;
import com.personalfinance.user.entity.IncomeSourceEntity;

/** Entity <-> DTO translation for income sources. */
@Component
public class IncomeMapper {

    public IncomeDto toDto(IncomeSourceEntity entity) {
        return IncomeDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .amount(entity.getAmount())
                .recurrence(entity.getRecurrence())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .build();
    }

    public List<IncomeDto> toDtos(List<IncomeSourceEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
