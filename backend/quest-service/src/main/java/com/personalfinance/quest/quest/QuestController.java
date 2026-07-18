package com.personalfinance.quest.quest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quests")
public class QuestController {

    public record QuestDto(UUID id, String templateCode, String title, String status,
            String kind, long target, long progress, String periodStart, String periodEnd,
            Map<String, Object> params) {

        static QuestDto of(QuestService.QuestView view) {
            QuestEntity q = view.quest();
            return new QuestDto(q.getId(), q.getTemplateCode(), q.getTitle(), q.getStatus(),
                    view.kind(), view.target(), view.progress(),
                    q.getPeriodStart().toString(), q.getPeriodEnd().toString(), q.getParams());
        }
    }

    private final QuestService service;

    public QuestController(QuestService service) {
        this.service = service;
    }

    @GetMapping
    public List<QuestDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt), LocalDate.now()).stream().map(QuestDto::of).toList();
    }

    @PostMapping("/{id}/accept")
    public Map<String, String> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Map.of("status", service.accept(id, userId(jwt)).getStatus());
    }

    @PostMapping("/{id}/decline")
    public Map<String, String> decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Map.of("status", service.decline(id, userId(jwt)).getStatus());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
