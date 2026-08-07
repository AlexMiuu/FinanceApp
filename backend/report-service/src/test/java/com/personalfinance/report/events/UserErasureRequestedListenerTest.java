package com.personalfinance.report.events;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.report.service.PrivacyService;

class UserErasureRequestedListenerTest {

    @Test
    void delegatesToPrivacyServiceWithTheEventsIds() {
        PrivacyService privacyService = mock(PrivacyService.class);
        UserErasureRequestedListener listener = new UserErasureRequestedListener(privacyService);

        UUID erasureRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        listener.onUserErasureRequested(new Events.UserErasureRequested(erasureRequestId, userId, Instant.now()));

        verify(privacyService).eraseUserData(erasureRequestId, userId);
    }

    @Test
    void swallowsAFailureInsteadOfLettingItRedeliverForever() {
        PrivacyService privacyService = mock(PrivacyService.class);
        UserErasureRequestedListener listener = new UserErasureRequestedListener(privacyService);
        UUID erasureRequestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("db down")).when(privacyService).eraseUserData(erasureRequestId, userId);

        // No exception should propagate out of the listener — with no DLQ, a rethrow
        // would requeue and redeliver immediately, looping forever on this message.
        listener.onUserErasureRequested(new Events.UserErasureRequested(erasureRequestId, userId, Instant.now()));

        verify(privacyService).eraseUserData(erasureRequestId, userId);
    }
}
