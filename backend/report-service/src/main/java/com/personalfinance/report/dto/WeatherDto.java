package com.personalfinance.report.dto;

import com.personalfinance.report.entity.WeatherBand;

public record WeatherDto(String band) {

    /**
     * The band served whenever the composite cannot be trusted — no state row, no
     * income on record, a failed computation. F4 never gates functionality, so
     * every degraded path resolves here rather than to an error.
     */
    public static WeatherDto clear() {
        return new WeatherDto(WeatherBand.CLEAR.wireName());
    }
}
