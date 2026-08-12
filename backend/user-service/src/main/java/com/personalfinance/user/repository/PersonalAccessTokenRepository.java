package com.personalfinance.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.personalfinance.user.entity.PersonalAccessTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessTokenEntity, UUID> {

    /** The introspection lookup: the gateway only ever knows the hash, never the id. */
    Optional<PersonalAccessTokenEntity> findByTokenHash(String tokenHash);

    List<PersonalAccessTokenEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Scoped by user id so one user's id cannot address another user's token. */
    Optional<PersonalAccessTokenEntity> findByIdAndUserId(UUID id, UUID userId);
}
