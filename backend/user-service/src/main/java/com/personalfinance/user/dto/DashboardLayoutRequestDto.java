package com.personalfinance.user.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The arrangement a client wants to save. This is also the persisted JSON shape,
 * so the column and the wire format cannot drift apart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLayoutRequestDto {

    @NotNull(message = "main column is required")
    private List<String> main;

    @NotNull(message = "side column is required")
    private List<String> side;
}
