package com.personalfinance.quest.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personalfinance.quest.entity.UserIncomeEntity;

public interface UserIncomeRepository extends JpaRepository<UserIncomeEntity, UUID> {

    long deleteByUserId(UUID userId);
}
