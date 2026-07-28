package com.personalfinance.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.personalfinance.user.entity.SavingsAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccountEntity, UUID> {

    List<SavingsAccountEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<SavingsAccountEntity> findByIdAndUserId(UUID id, UUID userId);
}
