package com.personalfinance.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.notification.entity.NotificationEntity;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    List<NotificationEntity> findByUserIdAndReadAtIsNull(UUID userId);

    long deleteByUserId(UUID userId);
}
