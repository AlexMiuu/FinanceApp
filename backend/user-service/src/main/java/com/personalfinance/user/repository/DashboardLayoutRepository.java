package com.personalfinance.user.repository;

import java.util.UUID;

import com.personalfinance.user.entity.DashboardLayoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardLayoutRepository extends JpaRepository<DashboardLayoutEntity, UUID> {
}
