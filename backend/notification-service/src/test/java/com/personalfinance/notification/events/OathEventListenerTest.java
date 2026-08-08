package com.personalfinance.notification.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.notification.service.NotificationService;

class OathEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final OathEventListener listener = new OathEventListener(notificationService);

    @Test
    void forwardsTheEventFieldsAndTheRoutingKeyToTheService() {
        UUID oathId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        listener.onOathEvent(new Events.OathEvent(oathId, userId, "Pledged 50 RON on Food", "KEPT", Instant.now()),
                "oath.kept");

        verify(notificationService).recordOathNotification(userId, "oath.kept", "Pledged 50 RON on Food", oathId);
    }

    @Test
    void swallowsAFailureInsteadOfLettingItRedeliverForever() {
        UUID oathId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(notificationService.recordOathNotification(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        // No exception should propagate — this queue has no DLQ, so a rethrow
        // would requeue and redeliver the same message forever.
        listener.onOathEvent(new Events.OathEvent(oathId, userId, "Pledged 50 RON on Food", "FORGONE", Instant.now()),
                "oath.forgone");

        verify(notificationService).recordOathNotification(userId, "oath.forgone", "Pledged 50 RON on Food", oathId);
    }
}
