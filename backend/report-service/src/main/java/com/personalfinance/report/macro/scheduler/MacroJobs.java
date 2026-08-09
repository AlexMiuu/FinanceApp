package com.personalfinance.report.macro.scheduler;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.personalfinance.report.macro.service.MacroService;

/**
 * Daily cron: CPI is a monthly print and ANRE tariffs change irregularly, so
 * a once-a-day sweep is ample cadence for F1's macro ingestion.
 */
@Component
public class MacroJobs {

    private static final Logger log = LoggerFactory.getLogger(MacroJobs.class);

    private final MacroService macroService;

    public MacroJobs(MacroService macroService) {
        this.macroService = macroService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void refresh() {
        try {
            macroService.refresh(LocalDate.now());
        } catch (Exception e) {
            log.error("Failed to refresh macro data", e);
        }
    }
}
