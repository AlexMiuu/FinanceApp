package com.personalfinance.report.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveReportRequestDto(@NotBlank @Size(max = 100) String name, @NotNull Map<String, Object> filters) {
}
