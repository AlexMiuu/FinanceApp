package com.personalfinance.notification.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.personalfinance.notification.service.NotificationService;

/** Persists quest events as notifications and pushes them live (FR-12). */
@Component
public class QuestEventListener {

    private static final Logger log = LoggerFactory.getLogger(QuestEventListener.class);

    private final NotificationService notificationService;

    public QuestEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Caught, not rethrown, for the same reason as
     * {@link UserErasureRequestedListener}: this queue has no DLQ, so a
     * rethrow requeues the message and redelivers it immediately, forever.
     */
    @RabbitListener(queues = EventsConfig.QUEST_QUEUE)
    public void onQuestEvent(Events.QuestEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            notificationService.recordQuestNotification(event.userId(), routingKey, event.title(), event.questId());
        } catch (Exception e) {
            log.error("Failed to record notification for {} (user {})", routingKey, event.userId(), e);
        }
    }
}
