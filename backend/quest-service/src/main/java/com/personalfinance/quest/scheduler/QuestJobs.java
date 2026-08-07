package com.personalfinance.quest.scheduler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.personalfinance.quest.service.QuestService;

@Component
public class QuestJobs {

    private final QuestService questService;

    public QuestJobs(QuestService questService) {
        this.questService = questService;
    }

    /** Closes out quests whose period ended; reads also finalize lazily. */
    @Scheduled(cron = "0 15 0 * * *")
    public void finalizeExpiredQuests() {
        questService.finalizeExpired(LocalDate.now());
    }
}
