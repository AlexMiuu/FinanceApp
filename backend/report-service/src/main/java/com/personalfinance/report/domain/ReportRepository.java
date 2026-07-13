package com.personalfinance.report.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<ReportEntity, UUID> {

    List<ReportEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ReportEntity> findByIdAndUserId(UUID id, UUID userId);
}
