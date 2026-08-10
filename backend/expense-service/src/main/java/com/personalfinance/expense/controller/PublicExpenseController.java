package com.personalfinance.expense.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.personalfinance.expense.service.ExpenseService;

import lombok.RequiredArgsConstructor;

/**
 * Read-only mirror of the first-party expense list for personal-access-token
 * clients. The gateway validates the token and forwards an internal JWT, so the
 * principal here is identical; the difference is that this path exposes no write
 * verbs, which is what stops a read-scoped token from writing.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/expenses")
public class PublicExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return expenseService.list(CurrentUser.id(jwt), from, to, categoryId, page, size);
    }
}
