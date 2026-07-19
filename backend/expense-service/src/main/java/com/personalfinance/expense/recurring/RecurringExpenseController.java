package com.personalfinance.expense.recurring;

import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.expense.category.CategoryController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/expenses/recurring")
public class RecurringExpenseController {

    public record RecurringDto(UUID id, UUID categoryId, long amount, String note, int dayOfMonth,
            LocalDate nextRun, boolean active) {

        static RecurringDto of(RecurringExpenseEntity e) {
            return new RecurringDto(e.getId(), e.getCategoryId(), e.getAmount(), e.getNote(),
                    e.getDayOfMonth(), e.getNextRun(), e.isActive());
        }
    }

    public record CreateRequest(
            @Positive long amount,
            @NotNull UUID categoryId,
            @Size(max = 500) String note,
            @NotNull LocalDate startDate) {
    }

    private final RecurringExpenseService service;

    public RecurringExpenseController(RecurringExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecurringDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CategoryController.userId(jwt)).stream().map(RecurringDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
        return RecurringDto.of(service.create(CategoryController.userId(jwt), request.categoryId(),
                request.amount(), request.note(), request.startDate(), LocalDate.now()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CategoryController.userId(jwt));
    }
}
