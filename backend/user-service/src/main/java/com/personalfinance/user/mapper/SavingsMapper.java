package com.personalfinance.user.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.user.dto.SavingsDto;
import com.personalfinance.user.entity.SavingsAccountEntity;

/** Entity <-> DTO translation for savings accounts. */
@Component
public class SavingsMapper {

    public SavingsDto toDto(SavingsAccountEntity entity) {
        return SavingsDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .balance(entity.getBalance())
                .build();
    }

    public List<SavingsDto> toDtos(List<SavingsAccountEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }
}
