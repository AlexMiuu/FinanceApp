package com.personalfinance.user.controller;

import java.util.UUID;

import com.personalfinance.user.dto.AccountDeleteRequestDto;
import com.personalfinance.user.service.AccountDeletionService;
import com.personalfinance.user.service.DataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Account erasure and personal-data export (GDPR Art. 17 and Art. 20). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class AccountController {

    private final AccountDeletionService accountDeletionService;
    private final DataExportService dataExportService;

    /**
     * Returns 204 once this service's data is gone and the fanout is queued;
     * the other services complete asynchronously against the same request id.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AccountDeleteRequestDto request) {
        accountDeletionService.initiateErasure(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/data-export")
    public ResponseEntity<?> dataExport(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(dataExportService.exportFor(userId));
    }
}
