package com.personalfinance.user.money;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxConfigRepository extends JpaRepository<TaxConfigEntity, UUID> {

    Optional<TaxConfigEntity> findFirstByValidFromLessThanEqualOrderByValidFromDesc(LocalDate date);
}
