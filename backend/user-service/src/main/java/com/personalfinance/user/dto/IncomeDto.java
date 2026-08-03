package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class IncomeDto {

    private UUID id;
    private String name;
    private long amount;
    private String recurrence;
    private LocalDate startDate;
    private LocalDate endDate;

}
