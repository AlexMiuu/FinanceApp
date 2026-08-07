package com.personalfinance.quest.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.personalfinance.quest.service.QuestService;

class QuestJobsTest {

    @Test
    void theNightlyJobFinalizesExpiredQuestsForTheCurrentDate() {
        QuestService questService = mock(QuestService.class);

        new QuestJobs(questService).finalizeExpiredQuests();

        verify(questService).finalizeExpired(any(LocalDate.class));
    }
}
