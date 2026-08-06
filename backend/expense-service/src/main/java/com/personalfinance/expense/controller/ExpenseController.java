package com.personalfinance.expense.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.expense.dto.ExpenseRequestDto;
import com.personalfinance.expense.service.ExpenseService;
import com.personalfinance.expense.service.PrivacyService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final PrivacyService privacyService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return expenseService.list(CurrentUser.id(jwt), from, to, categoryId, page, size);
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ExpenseRequestDto request) {
        return expenseService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequestDto request) {
        return expenseService.update(id, CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return expenseService.delete(id, CurrentUser.id(jwt));
    }

    @GetMapping("/export/me")
    public ResponseEntity<?> exportMyData(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(privacyService.exportUserData(CurrentUser.id(jwt)));
    }
}
