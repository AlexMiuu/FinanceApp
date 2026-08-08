package com.personalfinance.quest.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.quest.dto.OathCreateRequestDto;
import com.personalfinance.quest.dto.OathDto;
import com.personalfinance.quest.mapper.OathMapper;
import com.personalfinance.quest.service.OathService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/oaths")
public class OathController {

    private final OathService service;
    private final OathMapper mapper;

    public OathController(OathService service, OathMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<OathDto> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status) {
        return mapper.toDtos(service.list(userId(jwt), status));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OathDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OathCreateRequestDto request) {
        return mapper.toDto(service.create(userId(jwt), request.categoryId(), request.pledgedAmount(),
                request.expiresAt(), Instant.now()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.cancel(id, userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
