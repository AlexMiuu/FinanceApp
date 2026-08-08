package com.personalfinance.report.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.report.entity.WeatherStateEntity;

public interface WeatherStateRepository extends JpaRepository<WeatherStateEntity, UUID> {

    long deleteByUserId(UUID userId);
}
