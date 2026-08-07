package com.personalfinance.report.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.report.entity.CategoryProjectionEntity;

public interface CategoryProjectionRepository extends JpaRepository<CategoryProjectionEntity, UUID> {

    List<CategoryProjectionEntity> findByUserIdOrderByNameAsc(UUID userId);

    long deleteByUserId(UUID userId);
}
