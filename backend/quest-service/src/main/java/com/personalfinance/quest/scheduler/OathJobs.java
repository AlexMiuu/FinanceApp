package com.personalfinance.quest.scheduler;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.personalfinance.quest.service.OathService;

@Component
public class OathJobs {

    private final OathService oathService;

    public OathJobs(OathService oathService) {
        this.oathService = oathService;
    }

    /**
     * A DB sweep rather than delayed messages: the open set stays queryable and
     * needs no broker plugin. Minute granularity is ample for a purchase pledge.
     */
    @Scheduled(cron = "0 * * * * *")
    public void expireOpenOaths() {
        oathService.expireOpenOaths(Instant.now());
    }
}
