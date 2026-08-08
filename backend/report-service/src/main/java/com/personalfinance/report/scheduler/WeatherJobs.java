package com.personalfinance.report.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.personalfinance.report.repository.UserIncomeRepository;
import com.personalfinance.report.service.WeatherService;

/**
 * Scheduled, not event-triggered: a 6-hour dwell makes an hour of detection
 * latency immaterial, and recomputing from the RabbitMQ listener would let a
 * recompute failure mark the projection transaction rollback-only, requeuing
 * the message forever on a queue with no DLQ.
 */
@Component
public class WeatherJobs {

    private static final Logger log = LoggerFactory.getLogger(WeatherJobs.class);

    private final UserIncomeRepository incomes;
    private final WeatherService weatherService;

    public WeatherJobs(UserIncomeRepository incomes, WeatherService weatherService) {
        this.incomes = incomes;
        this.weatherService = weatherService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void recomputeAll() {
        Instant now = Instant.now();
        incomes.findAll().forEach(income -> {
            try {
                weatherService.recompute(income.getUserId(), now);
            } catch (Exception e) {
                log.error("Failed to recompute weather for {}", income.getUserId(), e);
            }
        });
    }
}
