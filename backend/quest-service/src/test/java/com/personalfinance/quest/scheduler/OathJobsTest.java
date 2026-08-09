package com.personalfinance.quest.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.personalfinance.quest.service.OathService;

class OathJobsTest {

    @Test
    void theSweepClosesOathsWhoseDeadlineHasPassed() {
        OathService oathService = mock(OathService.class);

        new OathJobs(oathService).expireOpenOaths();

        verify(oathService).expireOpenOaths(any(Instant.class));
    }
}
