package com.personalfinance.notification.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    List<NotificationEntity> findByUserIdAndReadAtIsNull(UUID userId);
}
