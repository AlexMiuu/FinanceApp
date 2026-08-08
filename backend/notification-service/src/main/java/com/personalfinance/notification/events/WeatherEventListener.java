package com.personalfinance.notification.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.personalfinance.notification.service.NotificationService;

/** Relays F4 band transitions to the user's live socket (FR ambient surface). */
@Component
public class WeatherEventListener {

    private static final Logger log = LoggerFactory.getLogger(WeatherEventListener.class);

    private final NotificationService notificationService;

    public WeatherEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Caught, not rethrown, for the same reason as {@link QuestEventListener}:
     * this queue has no DLQ, so a rethrow requeues the message and redelivers it
     * immediately, forever.
     */
    @RabbitListener(queues = EventsConfig.WEATHER_QUEUE)
    public void onWeatherEvent(Events.WeatherEvent event) {
        try {
            notificationService.broadcastWeather(event.userId(), event.band());
        } catch (Exception e) {
            log.error("Failed to broadcast weather for user {}", event.userId(), e);
        }
    }
}
