package com.personalfinance.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.personalfinance.user.entity.AuthIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentityEntity, UUID> {

    Optional<AuthIdentityEntity> findByProviderAndProviderUid(String provider, String providerUid);

    List<AuthIdentityEntity> findByUser_IdOrderByCreatedAtAsc(UUID userId);
}
