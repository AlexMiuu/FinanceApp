package com.personalfinance.user.mapper;

import org.springframework.stereotype.Component;

import com.personalfinance.user.dto.AuthDtos;
import com.personalfinance.user.entity.UserEntity;

/** Entity -> DTO translation for the authenticated user's profile. */
@Component
public class UserMapper {

    public AuthDtos.UserDto toDto(UserEntity user) {
        return new AuthDtos.UserDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl());
    }
}
