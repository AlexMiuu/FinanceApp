package com.personalfinance.expense.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.expense.domain.CategoryRepository;
import com.personalfinance.expense.expense.ExpenseService;
import com.personalfinance.expense.web.ApiExceptions.NotFoundException;

/**
 * Monthly recurring templates: the robustness layer for the manual flow.
 * Posting goes through ExpenseService.create, so projections, goals, and
 * quests see recurring expenses exactly like typed ones. Catch-up semantics:
 * any template whose next_run is in the past posts every missed occurrence —
 * this covers downtime AND lets a backdated template auto-fill history.
 */
@Service
public class RecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);
    /** Safety valve for pathological backdates (10 years of months). */
    private static final int MAX_CATCH_UP = 120;

    private final RecurringExpenseRepository recurring;
    private final CategoryRepository categories;
    private final ExpenseService expenseService;

    public RecurringExpenseService(RecurringExpenseRepository recurring, CategoryRepository categories,
            ExpenseService expenseService) {
        this.recurring = recurring;
        this.categories = categories;
        this.expenseService = expenseService;
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseEntity> list(UUID userId) {
        return recurring.findByUserIdOrderByCreatedAtAsc(userId);
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

    @Transactional
    public void delete(UUID id, UUID userId) {
        RecurringExpenseEntity template = recurring.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring expense not found"));
        recurring.delete(template);
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
