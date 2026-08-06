package com.personalfinance.expense.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.expense.entity.CategoryEntity;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    List<CategoryEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<CategoryEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByParentId(UUID parentId);

    boolean existsByUserIdAndParentIdAndNameIgnoreCase(UUID userId, UUID parentId, String name);

    long deleteByUserId(UUID userId);

    /** Subcategories must go before their parents: parent_id is a self-referencing FK with no cascade. */
    long deleteByUserIdAndParentIdIsNotNull(UUID userId);
}
