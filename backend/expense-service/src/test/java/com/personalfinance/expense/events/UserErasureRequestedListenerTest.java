package com.personalfinance.expense.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.expense.service.PrivacyService;

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
}
