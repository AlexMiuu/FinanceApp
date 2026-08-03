package com.personalfinance.user.controller;

import java.util.UUID;

import com.personalfinance.user.dto.IncomeRequestDto;
import com.personalfinance.user.dto.SavingsRequestDto;
import com.personalfinance.user.service.IncomeService;
import com.personalfinance.user.service.NetWorthService;
import com.personalfinance.user.service.SavingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Income sources, savings, and net worth */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class MoneyController {

    private final IncomeService incomeService;
    private final SavingsService savingsService;
    private final NetWorthService netWorthService;

    // ---- income sources ----

    @GetMapping("/income-sources")
    public ResponseEntity<?> listIncome(@AuthenticationPrincipal UUID userId) {
        return incomeService.listAllIncome(userId);
    }

    @PostMapping("/income-sources")
    public ResponseEntity<?> createIncome(@AuthenticationPrincipal UUID userId, @Valid @RequestBody IncomeRequestDto request) {
        return incomeService.createIncome(userId,request);
    }

    @PutMapping("/income-sources/{id}")
    public ResponseEntity<?> updateIncome(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody IncomeRequestDto request) {
        return incomeService.updateIncome(userId,id,request);
    }

    @DeleteMapping("/income-sources/{id}")
    public ResponseEntity<?> deleteIncome(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return incomeService.deleteIncome(userId,id);
    }

    // ---- savings ----

    @GetMapping("/savings")
    public ResponseEntity<?> listSavings(@AuthenticationPrincipal UUID userId) {
        return savingsService.listAllSavings(userId);
    }

    @PostMapping("/savings")
    public ResponseEntity<?> createSavings(@AuthenticationPrincipal UUID userId, @Valid @RequestBody SavingsRequestDto request) {
        return savingsService.createSavings(userId,request);
    }

    @PutMapping("/savings/{id}")
    public ResponseEntity<?> updateSavings(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody SavingsRequestDto request) {
        return savingsService.updateSavings(userId,id,request);
    }

    @DeleteMapping("/savings/{id}")
    public ResponseEntity<?> deleteSavings(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return savingsService.deleteSavings(userId,id);
    }

    // ---- net worth (FR-8) ----

    @GetMapping("/net-worth")
    public ResponseEntity<?> netWorth(@AuthenticationPrincipal UUID userId) {
        return netWorthService.netWorth(userId);
    }
}
