package com.personalfinance.user.mapper;

import org.springframework.stereotype.Component;

import com.personalfinance.user.dto.PersonalAccessTokenCreatedDto;
import com.personalfinance.user.dto.PersonalAccessTokenDto;
import com.personalfinance.user.entity.PersonalAccessTokenEntity;

/**
 * Entity to DTO for personal access tokens. Neither mapping reads
 * {@code tokenHash}, which is what keeps the digest off the wire; the raw token
 * is passed in separately by the one caller allowed to return it.
 */
@Component
public class PersonalAccessTokenMapper {

    public PersonalAccessTokenDto toDto(PersonalAccessTokenEntity entity) {
        return PersonalAccessTokenDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .scope(entity.getScope())
                .createdAt(entity.getCreatedAt())
                .lastUsedAt(entity.getLastUsedAt())
                .revokedAt(entity.getRevokedAt())
                .build();
    }

    public PersonalAccessTokenCreatedDto toCreatedDto(PersonalAccessTokenEntity entity, String rawToken) {
        return PersonalAccessTokenCreatedDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .scope(entity.getScope())
                .createdAt(entity.getCreatedAt())
                .lastUsedAt(entity.getLastUsedAt())
                .revokedAt(entity.getRevokedAt())
                .token(rawToken)
                .build();
    }
}
