package com.personalfinance.user.controller;

import com.personalfinance.user.dto.SalaryRequestDto;
import com.personalfinance.user.service.SalaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/salary-calculator")
public class SalaryController {

    private final SalaryService salaryService;

    @PostMapping
    public ResponseEntity<?> calculate(@Valid @RequestBody SalaryRequestDto request) {
        return salaryService.calculate(request);
    }
}
