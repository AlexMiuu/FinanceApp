package com.personalfinance.report.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.report.dto.WeatherDto;
import com.personalfinance.report.service.WeatherService;

/**
 * A returning session needs its band without waiting for the next STOMP push
 * (which only fires on a committed transition) — this is the catch-up read.
 */
@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService service;

    public WeatherController(WeatherService service) {
        this.service = service;
    }

    @GetMapping
    public WeatherDto current(@AuthenticationPrincipal Jwt jwt) {
        return service.currentFor(UUID.fromString(jwt.getSubject()));
    }
}
