package com.personalfinance.report.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.report.dto.DashboardDto;
import com.personalfinance.report.service.DashboardService;

/**
 * Read-only mirror of the first-party dashboard for personal-access-token
 * clients. {@link DashboardDto} already carries the ghost series, so the public
 * surface needs no separate ghost endpoint.
 */
@RestController
@RequestMapping("/api/v1/public/dashboard")
public class PublicDashboardController {

    private final DashboardService service;

    public PublicDashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardDto dashboard(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String month) {
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now();
        return service.build(UUID.fromString(jwt.getSubject()), target, LocalDate.now());
    }
}
