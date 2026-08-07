package com.personalfinance.user.controller;

import java.util.UUID;

import com.personalfinance.user.dto.DashboardLayoutRequestDto;
import com.personalfinance.user.service.DashboardLayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** The signed-in user's dashboard widget arrangement (M10). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me/dashboard-layout")
public class DashboardLayoutController {

    private final DashboardLayoutService dashboardLayoutService;

    @GetMapping
    public ResponseEntity<?> layout(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(dashboardLayoutService.layoutFor(userId));
    }

    @PutMapping
    public ResponseEntity<?> saveLayout(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody DashboardLayoutRequestDto request) {
        return ResponseEntity.ok(dashboardLayoutService.saveLayout(userId, request));
    }
}
