package com.personalfinance.quest.quest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestRepository extends JpaRepository<QuestEntity, UUID> {

    List<QuestEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<QuestEntity> findByIdAndUserId(UUID id, UUID userId);

    List<QuestEntity> findByUserIdAndStatus(UUID userId, String status);

    boolean existsByUserIdAndTemplateCodeAndPeriodStart(UUID userId, String templateCode, LocalDate periodStart);

    List<QuestEntity> findByStatusAndPeriodEndBefore(String status, LocalDate date);
}
