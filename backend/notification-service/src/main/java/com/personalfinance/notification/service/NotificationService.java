package com.personalfinance.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.notification.dto.NotificationDto;
import com.personalfinance.notification.dto.NotificationListDto;
import com.personalfinance.notification.entity.NotificationEntity;
import com.personalfinance.notification.exception.NotFoundException;
import com.personalfinance.notification.mapper.NotificationMapper;
import com.personalfinance.notification.repository.NotificationRepository;

@Service
public class NotificationService {

    private static final String DEFAULT_QUEST_BODY = "A quest was updated.";
    private static final String DEFAULT_OATH_BODY = "An oath was settled.";

    private final NotificationRepository notifications;
    private final NotificationMapper mapper;
    private final SimpMessagingTemplate messaging;

    public NotificationService(NotificationRepository notifications, NotificationMapper mapper,
            SimpMessagingTemplate messaging) {
        this.notifications = notifications;
        this.mapper = mapper;
        this.messaging = messaging;
    }

    @Transactional(readOnly = true)
    public NotificationListDto listFor(UUID userId) {
        requireUserId(userId);
        return new NotificationListDto(
                mapper.toDtos(notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId)),
                notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markRead(UUID id, UUID userId) {
        requireUserId(userId);
        if (id == null) {
            throw new NotFoundException("Notification not found");
        }
        notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"))
                .markRead();
    }

    @Transactional
    public void markAllRead(UUID userId) {
        requireUserId(userId);
        notifications.findByUserIdAndReadAtIsNull(userId).forEach(NotificationEntity::markRead);
    }

    /**
     * Persists a quest event as a notification and pushes it to the user's live
     * socket (FR-12). Title, body and payload are defaulted rather than passed
     * through: {@code title} and {@code body} are NOT NULL in the schema, and a
     * constraint violation here would roll back inside a RabbitMQ listener that
     * has no DLQ — the message would requeue and redeliver forever.
     */
    @Transactional
    public NotificationDto recordQuestNotification(UUID userId, String routingKey, String questTitle, UUID questId) {
        requireUserId(userId);
        String type = routingKey == null ? "quest.updated" : routingKey;
        String body = questTitle == null || questTitle.isBlank() ? DEFAULT_QUEST_BODY : questTitle;

        Map<String, Object> data = new LinkedHashMap<>();
        if (questId != null) {
            data.put("questId", questId.toString());
        }

        NotificationEntity saved = notifications.save(
                new NotificationEntity(userId, type, questNotificationTitle(type), body, data));

        NotificationDto dto = mapper.toDto(saved);
        messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", dto);
        return dto;
    }

    /** Same defaulting contract as {@link #recordQuestNotification}, for the same reason. */
    @Transactional
    public NotificationDto recordOathNotification(UUID userId, String routingKey, String oathTitle, UUID oathId) {
        requireUserId(userId);
        String type = routingKey == null ? "oath.updated" : routingKey;
        String body = oathTitle == null || oathTitle.isBlank() ? DEFAULT_OATH_BODY : oathTitle;

        Map<String, Object> data = new LinkedHashMap<>();
        if (oathId != null) {
            data.put("oathId", oathId.toString());
        }

        NotificationEntity saved = notifications.save(
                new NotificationEntity(userId, type, oathNotificationTitle(type), body, data));

        NotificationDto dto = mapper.toDto(saved);
        messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", dto);
        return dto;
    }

    /** FORGONE is an achievement, not a failure: the user chose not to spend. */
    private static String oathNotificationTitle(String routingKey) {
        return switch (routingKey) {
            case "oath.kept" -> "Oath kept";
            case "oath.slipped" -> "Oath slipped";
            case "oath.forgone" -> "Oath forgone, you held off";
            default -> "Oath update";
        };
    }

    private static String questNotificationTitle(String routingKey) {
        return switch (routingKey) {
            case "quest.completed" -> "Quest completed 🎉";
            case "quest.failed" -> "Quest failed";
            case "quest.suggested" -> "New quest suggested";
            default -> "Quest update";
        };
    }

    /**
     * Ambient state, not a user-facing notification — nothing to page through, so
     * unlike {@link #recordQuestNotification}, no {@link NotificationEntity} row is
     * persisted. Broadcast-only.
     */
    public void broadcastWeather(UUID userId, String band) {
        requireUserId(userId);
        messaging.convertAndSendToUser(userId.toString(), "/queue/weather", Map.of("band", band));
    }

    private static void requireUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
