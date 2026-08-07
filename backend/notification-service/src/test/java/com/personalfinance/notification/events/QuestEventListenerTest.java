package com.personalfinance.notification.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.notification.service.NotificationService;

class QuestEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final QuestEventListener listener = new QuestEventListener(notificationService);

    @Test
    void forwardsTheEventFieldsAndTheRoutingKeyToTheService() {
        UUID questId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        listener.onQuestEvent(new Events.QuestEvent(questId, userId, "Walk the flock", "COMPLETED", Instant.now()),
                "quest.completed");

        verify(notificationService).recordQuestNotification(userId, "quest.completed", "Walk the flock", questId);
    }

    @Test
    void swallowsAFailureInsteadOfLettingItRedeliverForever() {
        UUID questId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationService.recordQuestNotification(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        // No exception should propagate — this queue has no DLQ, so a rethrow
        // would requeue and redeliver the same message forever.
        listener.onQuestEvent(new Events.QuestEvent(questId, userId, "Walk the flock", "COMPLETED", Instant.now()),
                "quest.completed");

        verify(notificationService).recordQuestNotification(userId, "quest.completed", "Walk the flock", questId);
    }
}
