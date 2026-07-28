package com.personalfinance.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.personalfinance.user.entity.IncomeSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeSourceRepository extends JpaRepository<IncomeSourceEntity, UUID> {

    List<IncomeSourceEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<IncomeSourceEntity> findByIdAndUserId(UUID id, UUID userId);
}
