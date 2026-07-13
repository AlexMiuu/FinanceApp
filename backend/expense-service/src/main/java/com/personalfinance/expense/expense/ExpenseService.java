package com.personalfinance.expense.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.category.CategoryService;
import com.personalfinance.expense.domain.CategoryEntity;
import com.personalfinance.expense.domain.CategoryRepository;
import com.personalfinance.expense.domain.ExpenseEntity;
import com.personalfinance.expense.domain.ExpenseRepository;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.web.ApiExceptions.NotFoundException;

@Service
public class ExpenseService {

    private final ExpenseRepository expenses;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher events;

    public ExpenseService(ExpenseRepository expenses, CategoryRepository categories,
            CategoryService categoryService, ApplicationEventPublisher events) {
        this.expenses = expenses;
        this.categories = categories;
        this.categoryService = categoryService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<ExpenseEntity> search(UUID userId, LocalDate from, LocalDate to, UUID categoryId,
            int page, int size) {
        // Predicates are added only when a filter is present: binding NULL into
        // "(:x is null or ...)" makes Postgres fail with "could not determine
        // data type of parameter".
        Specification<ExpenseEntity> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (from != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), to));
        }
        if (categoryId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt")));
        return expenses.findAll(spec, pageRequest);
    }

    @Transactional
    public ExpenseEntity create(UUID userId, UUID categoryId, long amount, String note, LocalDate expenseDate) {
        CategoryEntity category = ownedCategory(userId, categoryId);
        ExpenseEntity expense = new ExpenseEntity(userId, categoryId, amount, note, expenseDate);
        expenses.save(expense);
        publishChanged(expense, category, true);
        return expense;
    }

    @Transactional
    public ExpenseEntity update(UUID id, UUID userId, UUID categoryId, long amount, String note,
            LocalDate expenseDate) {
        ExpenseEntity expense = expenses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        CategoryEntity category = ownedCategory(userId, categoryId);
        expense.update(categoryId, amount, note, expenseDate);
        publishChanged(expense, category, false);
        return expense;
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        ExpenseEntity expense = expenses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        expenses.delete(expense);
        events.publishEvent(new Events.ExpenseDeleted(id, userId, Instant.now()));
    }

    private CategoryEntity ownedCategory(UUID userId, UUID categoryId) {
        return categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private void publishChanged(ExpenseEntity expense, CategoryEntity category, boolean created) {
        CategoryService.CategorySnapshot snapshot = categoryService.snapshotOf(category);
        events.publishEvent(new Events.ExpenseChanged(
                expense.getId(), expense.getUserId(), category.getId(),
                snapshot.path(), snapshot.effectiveMandatory(),
                expense.getAmount(), expense.getCurrency(), expense.getNote(), expense.getExpenseDate(),
                Instant.now(), created));
    }
}
