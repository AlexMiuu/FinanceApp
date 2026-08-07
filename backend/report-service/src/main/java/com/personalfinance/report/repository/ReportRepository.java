package com.personalfinance.report.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.report.entity.ReportEntity;

public interface ReportRepository extends JpaRepository<ReportEntity, UUID> {

    List<ReportEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ReportEntity> findByIdAndUserId(UUID id, UUID userId);

    long deleteByUserId(UUID userId);
}
