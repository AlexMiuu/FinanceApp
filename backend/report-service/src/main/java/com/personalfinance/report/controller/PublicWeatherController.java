package com.personalfinance.report.controller;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.report.dto.WeatherDto;
import com.personalfinance.report.service.WeatherService;

/** Read-only mirror of the first-party weather read for token clients. */
@RestController
@RequestMapping("/api/v1/public/weather")
public class PublicWeatherController {

    private final WeatherService service;

    public PublicWeatherController(WeatherService service) {
        this.service = service;
    }

    @GetMapping
    public WeatherDto current(@AuthenticationPrincipal Jwt jwt) {
        return service.currentFor(UUID.fromString(jwt.getSubject()));
    }
}
