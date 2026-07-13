package com.personalfinance.report.dashboard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardService.Dashboard dashboard(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String month) {
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now();
        return service.build(UUID.fromString(jwt.getSubject()), target, LocalDate.now());
    }
}
