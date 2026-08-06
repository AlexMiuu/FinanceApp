package com.personalfinance.user.repository;

import java.util.List;
import java.util.UUID;

import com.personalfinance.user.entity.ConsentRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecordEntity, UUID> {

    List<ConsentRecordEntity> findByUserIdOrderByGrantedAtAsc(UUID userId);
}
