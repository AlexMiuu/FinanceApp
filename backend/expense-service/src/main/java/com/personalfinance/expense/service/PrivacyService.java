package com.personalfinance.expense.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.dto.ExpenseDataExportDto;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.mapper.CategoryMapper;
import com.personalfinance.expense.mapper.ExpenseMapper;
import com.personalfinance.expense.mapper.RecurringExpenseMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.ExpenseRepository;
import com.personalfinance.expense.repository.RecurringExpenseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PrivacyService {

    private final ExpenseRepository expenses;
    private final CategoryRepository categories;
    private final RecurringExpenseRepository recurringExpenses;
    private final ExpenseMapper expenseMapper;
    private final CategoryMapper categoryMapper;
    private final RecurringExpenseMapper recurringExpenseMapper;
    private final ApplicationEventPublisher events;

    /**
     * Deletion order matters twice over: expenses and recurring_expenses both
     * hold a plain (non-cascading) FK to categories, so categories go last;
     * and categories.parent_id is itself a plain, non-cascading
     * self-reference, so subcategories must go before their parents within
     * that step. deleteByUserId alone has no ORDER BY and Hibernate has no
     * association metadata for parentId (it's a bare UUID column, not a
     * @ManyToOne), so it cannot infer this — every seeded account has
     * parent+child categories, so getting this wrong breaks erasure for
     * effectively every user.
     */
    @Transactional
    public void eraseUserData(UUID erasureRequestId, UUID userId) {
        recurringExpenses.deleteByUserId(userId);
        expenses.deleteByUserId(userId);
        categories.deleteByUserIdAndParentIdIsNotNull(userId);
        categories.deleteByUserId(userId);
        events.publishEvent(new Events.ErasureCompleted(erasureRequestId, userId, "expense", Instant.now()));
    }

    @Transactional(readOnly = true)
    public ExpenseDataExportDto exportUserData(UUID userId) {
        return new ExpenseDataExportDto(
                categoryMapper.toDtos(categories.findByUserIdOrderByNameAsc(userId)),
                expenseMapper.toDtos(expenses.findByUserId(userId)),
                recurringExpenseMapper.toDtos(recurringExpenses.findByUserIdOrderByCreatedAtAsc(userId)));
    }
}
