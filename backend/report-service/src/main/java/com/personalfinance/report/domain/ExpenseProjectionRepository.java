package com.personalfinance.report.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseProjectionRepository extends JpaRepository<ExpenseProjectionEntity, UUID>,
        JpaSpecificationExecutor<ExpenseProjectionEntity> {

    List<ExpenseProjectionEntity> findByUserIdAndExpenseDateBetween(UUID userId, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from ExpenseProjectionEntity e "
            + "where e.userId = :userId and e.expenseDate between :from and :to")
    long sumForRange(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Re-denormalizes path + effective mandatory flag after a category rename,
     * for expenses in the renamed category and in its direct children.
     */
    @Modifying
    @Query(value = """
            UPDATE expense_projection ep SET
              category_path = CASE WHEN c.parent_id IS NULL THEN c.name
                                   ELSE p.name || ' > ' || c.name END,
              is_mandatory = c.is_mandatory OR COALESCE(p.is_mandatory, false),
              updated_at = now()
            FROM category_projection c
            LEFT JOIN category_projection p ON p.category_id = c.parent_id
            WHERE ep.category_id = c.category_id
              AND (c.category_id = :categoryId OR c.parent_id = :categoryId)
            """, nativeQuery = true)
    void refreshCategoryDenormalization(@Param("categoryId") UUID categoryId);
}
