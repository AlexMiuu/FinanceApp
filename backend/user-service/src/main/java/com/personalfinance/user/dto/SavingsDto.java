package com.personalfinance.user.dto;


import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SavingsDto {

    private UUID id;
    private String name;
    private long balance;
}
