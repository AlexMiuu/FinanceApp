package com.personalfinance.expense.scheduler;

import java.time.LocalDate;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.personalfinance.expense.service.RecurringExpenseService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecurringExpenseJobs {

    private final RecurringExpenseService recurringExpenseService;

    /** Shortly after midnight (containers run Europe/Bucharest). */
    @Scheduled(cron = "0 5 0 * * *")
    public void postDueDaily() {
        recurringExpenseService.runDue(LocalDate.now());
    }

    /** Startup catch-up: occurrences missed while the stack was down. */
    @EventListener(ApplicationReadyEvent.class)
    public void postDueOnStartup() {
        recurringExpenseService.runDue(LocalDate.now());
    }
}
