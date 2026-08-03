package com.personalfinance.expense.service;

import static com.personalfinance.expense.service.RequestGuards.requireAmount;
import static com.personalfinance.expense.service.RequestGuards.requireBody;
import static com.personalfinance.expense.service.RequestGuards.requireId;
import static com.personalfinance.expense.service.RequestGuards.requireUser;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.dto.RecurringExpenseRequestDto;
import com.personalfinance.expense.entity.RecurringExpenseEntity;
import com.personalfinance.expense.exception.NotFoundException;
import com.personalfinance.expense.mapper.RecurringExpenseMapper;
import com.personalfinance.expense.repository.CategoryRepository;
import com.personalfinance.expense.repository.RecurringExpenseRepository;

import lombok.RequiredArgsConstructor;

/**
 * Monthly recurring templates: the robustness layer for the manual flow.
 * Posting goes through ExpenseService.create, so projections, goals, and
 * quests see recurring expenses exactly like typed ones. Catch-up semantics:
 * any template whose next_run is in the past posts every missed occurrence —
 * this covers downtime AND lets a backdated template auto-fill history.
 */
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);
    /** Safety valve for pathological backdates (10 years of months). */
    private static final int MAX_CATCH_UP = 120;

    private final RecurringExpenseRepository recurring;
    private final CategoryRepository categories;
    private final ExpenseService expenseService;
    private final RecurringExpenseMapper recurringExpenseMapper;

    @Transactional(readOnly = true)
    public ResponseEntity<?> list(UUID userId) {
        requireUser(userId);

        List<RecurringExpenseEntity> templates = recurring.findByUserIdOrderByCreatedAtAsc(userId);
        return ResponseEntity.ok(recurringExpenseMapper.toDtos(templates));
    }

    @Transactional
    public ResponseEntity<?> create(UUID userId, RecurringExpenseRequestDto request) {
        return create(userId, request, LocalDate.now());
    }

    /** Overload taking the clock so catch-up behaviour is unit-testable. */
    @Transactional
    public ResponseEntity<?> create(UUID userId, RecurringExpenseRequestDto request, LocalDate today) {
        requireUser(userId);
        requireBody(request);

        RecurringExpenseEntity template = create(userId, request.categoryId(),
                requireAmount(request.amount()), request.note(), request.startDate(), today);
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringExpenseMapper.toDto(template));
    }

    @Transactional
    public ResponseEntity<?> delete(UUID id, UUID userId) {
        requireUser(userId);
        requireId(id, "Recurring expense");

        RecurringExpenseEntity template = recurring.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring expense not found"));
        recurring.delete(template);
        return ResponseEntity.noContent().build();
    }

    /**
     * Creates the template and immediately posts everything due from startDate
     * to today, so "rent since January" fills the history in one call.
     */
    @Transactional
    public RecurringExpenseEntity create(UUID userId, UUID categoryId, long amount, String note,
            LocalDate startDate, LocalDate today) {
        categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        RecurringExpenseEntity template =
                recurring.save(new RecurringExpenseEntity(userId, categoryId, amount, note, startDate));
        postDue(template, today);
        return template;
    }

    /** Daily job + startup catch-up entry point. */
    @Transactional
    public int runDue(LocalDate today) {
        List<RecurringExpenseEntity> due = recurring.findByActiveTrueAndNextRunLessThanEqual(today);
        int posted = 0;
        for (RecurringExpenseEntity template : due) {
            posted += postDue(template, today);
        }
        if (posted > 0) {
            log.info("Posted {} recurring expense occurrence(s)", posted);
        }
        return posted;
    }

    private int postDue(RecurringExpenseEntity template, LocalDate today) {
        int posted = 0;
        while (!template.getNextRun().isAfter(today) && posted < MAX_CATCH_UP) {
            expenseService.create(template.getUserId(), template.getCategoryId(), template.getAmount(),
                    template.getNote(), template.getNextRun());
            template.advance();
            posted++;
        }
        return posted;
    }
}
