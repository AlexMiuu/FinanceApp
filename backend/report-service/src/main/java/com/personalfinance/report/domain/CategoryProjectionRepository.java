package com.personalfinance.report.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryProjectionRepository extends JpaRepository<CategoryProjectionEntity, UUID> {
}
