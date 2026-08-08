package com.personalfinance.report.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.report.entity.UserIncomeEntity;

public interface UserIncomeRepository extends JpaRepository<UserIncomeEntity, UUID> {

    long deleteByUserId(UUID userId);
}
