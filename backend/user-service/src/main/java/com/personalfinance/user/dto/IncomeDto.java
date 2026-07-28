package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class IncomeDto {

    public UUID id;
    public String name;
    public long amount;
    public String recurrence;
    public LocalDate startDate;
    public LocalDate endDate;

}
