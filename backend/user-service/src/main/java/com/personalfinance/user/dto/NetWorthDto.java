package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NetWorthDto {

    private long total;
    private int accounts;
    private long monthlyIncome;
}
