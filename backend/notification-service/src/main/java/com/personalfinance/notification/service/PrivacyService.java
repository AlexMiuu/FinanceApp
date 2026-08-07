package com.personalfinance.notification.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.notification.dto.NotificationDataExportDto;
import com.personalfinance.notification.events.Events;
import com.personalfinance.notification.mapper.NotificationMapper;
import com.personalfinance.notification.repository.NotificationRepository;

@Service
public class PrivacyService {

    private final NotificationRepository notifications;
    private final NotificationMapper mapper;
    private final ApplicationEventPublisher events;

    public PrivacyService(NotificationRepository notifications, NotificationMapper mapper,
            ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.mapper = mapper;
        this.events = events;
    }

    /**
     * {@code notifications} is the only table in notifications_db and carries no
     * foreign keys, so there is no delete ordering to respect — the single
     * delete-by-user is the whole erasure for this service.
     */
    @Transactional
    public void eraseUserData(UUID erasureRequestId, UUID userId) {
        requireUserId(userId);
        notifications.deleteByUserId(userId);
        events.publishEvent(new Events.ErasureCompleted(erasureRequestId, userId, "notification", Instant.now()));
    }

    @Transactional(readOnly = true)
    public NotificationDataExportDto exportUserData(UUID userId) {
        requireUserId(userId);
        return new NotificationDataExportDto(
                mapper.toExportDtos(notifications.findByUserIdOrderByCreatedAtDesc(userId)));
    }

    private static void requireUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
