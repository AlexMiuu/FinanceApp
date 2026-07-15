package com.personalfinance.quest.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryProjectionRepository extends JpaRepository<CategoryProjectionEntity, UUID> {

    List<CategoryProjectionEntity> findByParentId(UUID parentId);
}
