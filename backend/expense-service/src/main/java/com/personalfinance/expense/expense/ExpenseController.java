package com.personalfinance.expense.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

import com.personalfinance.expense.category.CategoryController;
import com.personalfinance.expense.domain.ExpenseEntity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    public record ExpenseDto(UUID id, long amount, String currency, UUID categoryId, String note,
            LocalDate expenseDate) {

        static ExpenseDto of(ExpenseEntity e) {
            return new ExpenseDto(e.getId(), e.getAmount(), e.getCurrency(), e.getCategoryId(),
                    e.getNote(), e.getExpenseDate());
        }
    }

    public record ExpenseRequest(
            @Positive long amount,
            @NotNull UUID categoryId,
            @Size(max = 500) String note,
            @NotNull LocalDate expenseDate) {
    }

    public record PageDto(List<ExpenseDto> items, int page, int size, long totalElements) {
    }

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public PageDto list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<ExpenseEntity> result = service.search(CategoryController.userId(jwt), from, to, categoryId, page, size);
        return new PageDto(
                result.getContent().stream().map(ExpenseDto::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ExpenseRequest request) {
        return ExpenseDto.of(service.create(CategoryController.userId(jwt), request.categoryId(),
                request.amount(), request.note(), request.expenseDate()));
    }

    @PutMapping("/{id}")
    public ExpenseDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {
        return ExpenseDto.of(service.update(id, CategoryController.userId(jwt), request.categoryId(),
                request.amount(), request.note(), request.expenseDate()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CategoryController.userId(jwt));
    }
}
