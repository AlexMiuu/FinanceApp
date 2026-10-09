package com.personalfinance.user.dto;

import com.personalfinance.user.service.AuthService.TokenPair;
import com.personalfinance.user.entity.UserEntity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public final class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserDto(String id, String email, String displayName, String avatarUrl) {

        public static UserDto of(UserEntity user) {
            return new UserDto(user.getId().toString(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
        }
    }

    public record AuthResponse(String accessToken, UserDto user) {

        public static AuthResponse of(TokenPair tokens) {
            return new AuthResponse(tokens.accessToken(), UserDto.of(tokens.user()));
        }
    }
}
