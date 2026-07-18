package com.personalfinance.quest.quest;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIncomeRepository extends JpaRepository<UserIncomeEntity, UUID> {
}
