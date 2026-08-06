package com.personalfinance.expense.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.expense.entity.RecurringExpenseEntity;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpenseEntity, UUID> {

    List<RecurringExpenseEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<RecurringExpenseEntity> findByIdAndUserId(UUID id, UUID userId);

    List<RecurringExpenseEntity> findByActiveTrueAndNextRunLessThanEqual(LocalDate date);

    long deleteByUserId(UUID userId);
}
