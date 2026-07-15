package com.personalfinance.quest.goal;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
public class GoalController {

    public record GoalDto(UUID id, String name, UUID categoryId, long targetAmount, String period,
            LocalDate startDate, LocalDate endDate, boolean active,
            long currentActual, boolean currentMet, LocalDate periodStart, LocalDate periodEnd) {

        static GoalDto of(GoalService.GoalStatus s) {
            GoalEntity g = s.goal();
            return new GoalDto(g.getId(), g.getName(), g.getCategoryId(), g.getTargetAmount(),
                    g.getPeriod(), g.getStartDate(), g.getEndDate(), g.isActive(),
                    s.currentActual(), s.currentMet(), s.periodStart(), s.periodEnd());
        }
    }

    public record GoalRequest(
            @NotBlank @Size(max = 100) String name,
            UUID categoryId,
            @Positive long targetAmount,
            @NotNull @Pattern(regexp = "DAILY|MONTHLY|YEARLY") String period,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean active) {
    }

    private final GoalService goalService;
    private final CalendarService calendarService;

    public GoalController(GoalService goalService, CalendarService calendarService) {
        this.goalService = goalService;
        this.calendarService = calendarService;
    }

    @GetMapping("/api/v1/goals")
    public List<GoalDto> list(@AuthenticationPrincipal Jwt jwt) {
        return goalService.list(userId(jwt), LocalDate.now()).stream().map(GoalDto::of).toList();
    }

    @PostMapping("/api/v1/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GoalRequest r) {
        GoalEntity goal = goalService.create(userId(jwt), r.name(), r.categoryId(), r.targetAmount(),
                r.period(), r.startDate(), r.endDate());
        return GoalDto.of(goalService.list(userId(jwt), LocalDate.now()).stream()
                .filter(s -> s.goal().getId().equals(goal.getId()))
                .findFirst().orElseThrow());
    }

    @PutMapping("/api/v1/goals/{id}")
    public GoalDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody GoalRequest r) {
        goalService.update(id, userId(jwt), r.name(), r.categoryId(), r.targetAmount(),
                r.period(), r.startDate(), r.endDate(), r.active() == null || r.active());
        return GoalDto.of(goalService.list(userId(jwt), LocalDate.now()).stream()
                .filter(s -> s.goal().getId().equals(id))
                .findFirst().orElseThrow());
    }

    @DeleteMapping("/api/v1/goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        goalService.delete(id, userId(jwt));
    }

    @GetMapping("/api/v1/calendar")
    public CalendarService.Calendar calendar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String month) {
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now();
        return calendarService.build(userId(jwt), target, LocalDate.now());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
