package com.personalfinance.user.money;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccountEntity, UUID> {

    List<SavingsAccountEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<SavingsAccountEntity> findByIdAndUserId(UUID id, UUID userId);
}
