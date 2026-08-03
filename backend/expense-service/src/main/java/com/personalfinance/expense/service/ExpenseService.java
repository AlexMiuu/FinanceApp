package com.personalfinance.expense.service;

import static com.personalfinance.expense.service.RequestGuards.requireAmount;
import static com.personalfinance.expense.service.RequestGuards.requireBody;
import static com.personalfinance.expense.service.RequestGuards.requireId;
import static com.personalfinance.expense.service.RequestGuards.requireUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.dto.ExpenseRequestDto;
import com.personalfinance.expense.dto.PageDto;
import com.personalfinance.expense.entity.CategoryEntity;
import com.personalfinance.expense.entity.ExpenseEntity;
import com.personalfinance.expense.events.Events;
import com.personalfinance.expense.exception.NotFoundException;
import com.personalfinance.expense.exception.UnprocessableException;
import com.personalfinance.expense.mapper.ExpenseMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.ExpenseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final int MAX_PAGE_SIZE = 200;

    private final ExpenseRepository expenses;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final ExpenseMapper expenseMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public ResponseEntity<?> list(UUID userId, LocalDate from, LocalDate to, UUID categoryId,
            int page, int size) {
        requireUser(userId);
        requireOrderedRange(from, to);

        Page<ExpenseEntity> result = search(userId, from, to, categoryId, page, size);
        return ResponseEntity.ok(PageDto.of(result, expenseMapper.toDtos(result.getContent())));
    }

    @Transactional
    public ResponseEntity<?> create(UUID userId, ExpenseRequestDto request) {
        requireUser(userId);
        requireBody(request);

        ExpenseEntity expense = create(userId, request.categoryId(), requireAmount(request.amount()),
                request.note(), request.expenseDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseMapper.toDto(expense));
    }

    @Transactional
    public ResponseEntity<?> update(UUID id, UUID userId, ExpenseRequestDto request) {
        requireUser(userId);
        requireId(id, "Expense");
        requireBody(request);

        ExpenseEntity expense = ownedExpense(id, userId);
        CategoryEntity category = ownedCategory(userId, request.categoryId());
        expense.update(request.categoryId(), requireAmount(request.amount()), request.note(),
                request.expenseDate());
        publishChanged(expense, category, false);
        return ResponseEntity.ok(expenseMapper.toDto(expense));
    }

    @Transactional
    public ResponseEntity<?> delete(UUID id, UUID userId) {
        requireUser(userId);
        requireId(id, "Expense");

        ExpenseEntity expense = ownedExpense(id, userId);
        expenses.delete(expense);
        events.publishEvent(new Events.ExpenseDeleted(id, userId, Instant.now()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Posting entry point shared with recurring templates, so a generated
     * expense reaches projections, goals, and quests exactly like a typed one.
     */
    @Transactional
    public ExpenseEntity create(UUID userId, UUID categoryId, long amount, String note, LocalDate expenseDate) {
        CategoryEntity category = ownedCategory(userId, categoryId);
        ExpenseEntity expense = new ExpenseEntity(userId, categoryId, amount, note, expenseDate);
        expenses.save(expense);
        publishChanged(expense, category, true);
        return expense;
    }

    private Page<ExpenseEntity> search(UUID userId, LocalDate from, LocalDate to, UUID categoryId,
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
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt")));
        return expenses.findAll(spec, pageRequest);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static void requireOrderedRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new UnprocessableException("'from' must not be after 'to'");
        }
    }

    private ExpenseEntity ownedExpense(UUID id, UUID userId) {
        return expenses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
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
