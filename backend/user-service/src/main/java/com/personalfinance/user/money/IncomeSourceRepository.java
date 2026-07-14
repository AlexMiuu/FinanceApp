package com.personalfinance.user.money;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeSourceRepository extends JpaRepository<IncomeSourceEntity, UUID> {

    List<IncomeSourceEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<IncomeSourceEntity> findByIdAndUserId(UUID id, UUID userId);
}
