package com.personalfinance.report.mapper;

import org.springframework.stereotype.Component;

import com.personalfinance.report.dto.WeatherDto;
import com.personalfinance.report.dto.WeatherStateExportDto;
import com.personalfinance.report.entity.WeatherBand;
import com.personalfinance.report.entity.WeatherStateEntity;

@Component
public class WeatherMapper {

    public WeatherDto toDto(WeatherStateEntity entity) {
        return new WeatherDto(entity.getCurrentBand().wireName());
    }

    public WeatherStateExportDto toExportDto(WeatherStateEntity entity) {
        return new WeatherStateExportDto(
                entity.getCurrentBand().wireName(),
                entity.getPendingBand().map(WeatherBand::wireName).orElse(null),
                entity.getPendingSince().orElse(null),
                entity.getUpdatedAt());
    }
}
