package com.personalfinance.quest.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.quest.dto.CalendarDto;
import com.personalfinance.quest.dto.GoalDto;
import com.personalfinance.quest.dto.GoalRequestDto;
import com.personalfinance.quest.entity.GoalEntity;
import com.personalfinance.quest.mapper.GoalMapper;
import com.personalfinance.quest.service.CalendarService;
import com.personalfinance.quest.service.GoalService;

import jakarta.validation.Valid;

@RestController
public class GoalController {

    private final GoalService goalService;
    private final CalendarService calendarService;
    private final GoalMapper mapper;

    public GoalController(GoalService goalService, CalendarService calendarService, GoalMapper mapper) {
        this.goalService = goalService;
        this.calendarService = calendarService;
        this.mapper = mapper;
    }

    @GetMapping("/api/v1/goals")
    public List<GoalDto> list(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toDtos(goalService.list(userId(jwt), LocalDate.now()));
    }

    @PostMapping("/api/v1/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GoalRequestDto request) {
        GoalEntity goal = goalService.create(userId(jwt), request.name(), request.categoryId(),
                request.targetAmount(), request.period(), request.startDate(), request.endDate());
        return mapper.toDto(goalService.get(goal.getId(), userId(jwt), LocalDate.now()));
    }

    @PutMapping("/api/v1/goals/{id}")
    public GoalDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody GoalRequestDto request) {
        goalService.update(id, userId(jwt), request.name(), request.categoryId(), request.targetAmount(),
                request.period(), request.startDate(), request.endDate(), request.activeOrDefault());
        return mapper.toDto(goalService.get(id, userId(jwt), LocalDate.now()));
    }

    @DeleteMapping("/api/v1/goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        goalService.delete(id, userId(jwt));
    }

    @GetMapping("/api/v1/calendar")
    public CalendarDto calendar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String month) {
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now();
        return calendarService.build(userId(jwt), target, LocalDate.now());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
