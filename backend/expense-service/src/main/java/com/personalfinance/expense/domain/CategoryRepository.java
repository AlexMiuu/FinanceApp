package com.personalfinance.expense.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<CategoryEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByParentId(UUID parentId);

    boolean existsByUserIdAndParentIdAndNameIgnoreCase(UUID userId, UUID parentId, String name);
}
