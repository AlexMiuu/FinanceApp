package com.personalfinance.user.dto;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * A user's dashboard arrangement: which widgets sit in the wide column and
 * which in the narrow one, each list in render order.
 */
@Data
@Builder
public class DashboardLayoutDto {

    private List<String> main;
    private List<String> side;

    /** Null until the user has saved an arrangement — the response is the default layout. */
    private Instant updatedAt;
}
