package com.personalfinance.quest.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.quest.dto.QuestDataExportDto;
import com.personalfinance.quest.dto.QuestDto;
import com.personalfinance.quest.dto.QuestStatusDto;
import com.personalfinance.quest.mapper.QuestMapper;
import com.personalfinance.quest.service.PrivacyService;
import com.personalfinance.quest.service.QuestService;

@RestController
@RequestMapping("/api/v1/quests")
public class QuestController {

    private final QuestService service;
    private final QuestMapper mapper;
    private final PrivacyService privacyService;

    public QuestController(QuestService service, QuestMapper mapper, PrivacyService privacyService) {
        this.service = service;
        this.mapper = mapper;
        this.privacyService = privacyService;
    }

    @GetMapping
    public List<QuestDto> list(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toDtos(service.list(userId(jwt), LocalDate.now()));
    }

    @PostMapping("/{id}/accept")
    public QuestStatusDto accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return new QuestStatusDto(service.accept(id, userId(jwt)).getStatus());
    }

    @PostMapping("/{id}/decline")
    public QuestStatusDto decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return new QuestStatusDto(service.decline(id, userId(jwt)).getStatus());
    }

    /** GDPR Art. 20 machine-readable export of this service's data classes (M9, per docs/records-of-processing.md). */
    @GetMapping("/export/me")
    public QuestDataExportDto exportMyData(@AuthenticationPrincipal Jwt jwt) {
        return privacyService.exportUserData(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
