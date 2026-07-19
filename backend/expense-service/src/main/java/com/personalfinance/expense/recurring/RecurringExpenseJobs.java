package com.personalfinance.expense.recurring;

import java.time.LocalDate;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringExpenseJobs {

    private final RecurringExpenseService service;

    public RecurringExpenseJobs(RecurringExpenseService service) {
        this.service = service;
    }

    /** Shortly after midnight (containers run Europe/Bucharest). */
    @Scheduled(cron = "0 5 0 * * *")
    public void postDueDaily() {
        service.runDue(LocalDate.now());
    }

    /** Startup catch-up: occurrences missed while the stack was down. */
    @EventListener(ApplicationReadyEvent.class)
    public void postDueOnStartup() {
        service.runDue(LocalDate.now());
    }
}
