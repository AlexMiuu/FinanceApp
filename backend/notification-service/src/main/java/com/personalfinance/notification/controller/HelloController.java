package com.personalfinance.notification.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M0 stub proving the service is reachable through the gateway.
 * Replaced by real endpoints in later milestones.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class HelloController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of(
                "service", "notification-service",
                "status", "ok",
                "milestone", "M0");
    }
}

