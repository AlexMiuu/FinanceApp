package com.personalfinance.quest.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.quest.dto.OathDto;
import com.personalfinance.quest.mapper.OathMapper;
import com.personalfinance.quest.service.OathService;

/** Read-only mirror of the first-party oath list for token clients. */
@RestController
@RequestMapping("/api/v1/public/oaths")
public class PublicOathController {

    private final OathService service;
    private final OathMapper mapper;

    public PublicOathController(OathService service, OathMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<OathDto> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status) {
        return mapper.toDtos(service.list(UUID.fromString(jwt.getSubject()), status));
    }
}
