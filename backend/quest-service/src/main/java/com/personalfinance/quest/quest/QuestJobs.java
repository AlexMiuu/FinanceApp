package com.personalfinance.quest.quest;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
