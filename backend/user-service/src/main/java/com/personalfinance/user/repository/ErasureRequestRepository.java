package com.personalfinance.user.repository;

import java.util.List;
import java.util.UUID;

import com.personalfinance.user.entity.ErasureRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErasureRequestRepository extends JpaRepository<ErasureRequestEntity, UUID> {

    List<ErasureRequestEntity> findByUserIdOrderByRequestedAtDesc(UUID userId);
}
