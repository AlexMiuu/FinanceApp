package com.personalfinance.report.dto;

import java.time.LocalDate;

public record DayPointDto(LocalDate date, long amount) {
}
