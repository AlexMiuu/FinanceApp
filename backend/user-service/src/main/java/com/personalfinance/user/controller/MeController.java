package com.personalfinance.user.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.user.dto.AuthDtos;
import com.personalfinance.user.repository.UserRepository;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor

public class MeController {

    private final UserRepository users;

    @GetMapping
    public AuthDtos.UserDto me(@AuthenticationPrincipal UUID userId) {
        return users.findById(userId)
                .map(AuthDtos.UserDto::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
