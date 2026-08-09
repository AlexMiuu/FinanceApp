package com.personalfinance.quest.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.personalfinance.quest.entity.OathEntity;

public interface OathRepository extends JpaRepository<OathEntity, UUID> {

    List<OathEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<OathEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<OathEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<OathEntity> findByIdAndUserId(UUID id, UUID userId);

    /** Reconciliation candidates: the pledged category itself or a child of it. */
    List<OathEntity> findByUserIdAndStatusAndCategoryIdInOrderByCreatedAtAsc(
            UUID userId, String status, Collection<UUID> categoryIds);

    List<OathEntity> findByStatusAndExpiresAtBefore(String status, Instant cutoff);

    boolean existsByMatchedExpenseId(UUID matchedExpenseId);

    long deleteByUserId(UUID userId);

    /**
     * Closes an oath only while it is still open, so an at-least-once redelivery
     * of the same expense.created cannot resolve it twice — the second call
     * updates zero rows and the caller skips the outbound event.
     *
     * <p>A bulk update bypasses {@code @PreUpdate}, hence the explicit
     * {@code updatedAt}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OathEntity o set o.status = :status, o.matchedExpenseId = :matchedExpenseId, "
            + "o.resolvedAt = :resolvedAt, o.updatedAt = :resolvedAt "
            + "where o.id = :id and o.status = 'OPEN'")
    int resolveIfOpen(@Param("id") UUID id, @Param("status") String status,
            @Param("matchedExpenseId") UUID matchedExpenseId, @Param("resolvedAt") Instant resolvedAt);
}
