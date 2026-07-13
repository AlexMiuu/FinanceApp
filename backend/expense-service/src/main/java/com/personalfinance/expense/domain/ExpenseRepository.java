package com.personalfinance.expense.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, UUID>,
        JpaSpecificationExecutor<ExpenseEntity> {

    Optional<ExpenseEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByCategoryId(UUID categoryId);
}
