package com.personalfinance.notification.events;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.notification.service.NotificationService;

class WeatherEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final WeatherEventListener listener = new WeatherEventListener(notificationService);

    @Test
    void forwardsTheUserIdAndBandToTheService() {
        UUID userId = UUID.randomUUID();

        listener.onWeatherEvent(new Events.WeatherEvent(userId, "storm", Instant.now()));

        verify(notificationService).broadcastWeather(userId, "storm");
    }

    @Test
    void swallowsAFailureInsteadOfLettingItRedeliverForever() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("db down")).when(notificationService).broadcastWeather(userId, "clear");

        // No exception should propagate — this queue has no DLQ, so a rethrow
        // would requeue and redeliver the same message forever.
        listener.onWeatherEvent(new Events.WeatherEvent(userId, "clear", Instant.now()));

        verify(notificationService).broadcastWeather(userId, "clear");
    }
}
