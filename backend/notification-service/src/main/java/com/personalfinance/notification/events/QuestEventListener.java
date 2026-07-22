package com.personalfinance.notification.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.notification.domain.NotificationEntity;
import com.personalfinance.notification.domain.NotificationRepository;
import com.personalfinance.notification.controller.NotificationController;

/** Persists quest events as notifications and pushes them live (FR-12). */
@Component
public class QuestEventListener {

    public record QuestEvent(UUID questId, UUID userId, String title, String status, Instant occurredAt) {
    }

    private final NotificationRepository notifications;
    private final SimpMessagingTemplate messaging;

    public QuestEventListener(NotificationRepository notifications, SimpMessagingTemplate messaging) {
        this.notifications = notifications;
        this.messaging = messaging;
    }

    @Transactional
    @RabbitListener(queues = EventsConfig.QUEST_QUEUE)
    public void onQuestEvent(QuestEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String title = switch (routingKey) {
            case "quest.completed" -> "Quest completed 🎉";
            case "quest.failed" -> "Quest failed";
            case "quest.suggested" -> "New quest suggested";
            default -> "Quest update";
        };
        NotificationEntity notification = notifications.save(new NotificationEntity(
                event.userId(), routingKey, title, event.title(),
                Map.of("questId", event.questId().toString())));

        messaging.convertAndSendToUser(event.userId().toString(), "/queue/notifications",
                NotificationController.NotificationDto.of(notification));
    }
}
