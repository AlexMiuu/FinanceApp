package com.personalfinance.user.controller;

import java.util.UUID;

import com.personalfinance.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<?> me(@AuthenticationPrincipal UUID userId) {
        return userProfileService.currentUser(userId);
    }
}
