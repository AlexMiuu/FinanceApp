package com.personalfinance.report.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.personalfinance.report.entity.CategoryProjectionEntity;
import com.personalfinance.report.entity.ExpenseProjectionEntity;
import com.personalfinance.report.entity.UserIncomeEntity;
import com.personalfinance.report.repository.CategoryProjectionRepository;
import com.personalfinance.report.repository.ExpenseProjectionRepository;
import com.personalfinance.report.repository.UserIncomeRepository;

/**
 * Maintains the local read models from expense-service events. Handlers are
 * idempotent (PK upsert / delete-by-id), so redelivery is harmless.
 */
@Component
public class ProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);

    /** Loose mirror of expense-service payloads; unknown JSON fields are ignored. */
    public record ExpenseEvent(UUID expenseId, UUID userId, UUID categoryId, String categoryPath,
            Boolean categoryMandatory, Long amount, String currency, String note, LocalDate expenseDate,
            Instant occurredAt) {
    }

    public record CategoryEvent(UUID categoryId, UUID userId, String name, UUID parentId,
            Boolean mandatory, Instant occurredAt) {
    }

    public record IncomeEvent(UUID userId, Long monthlyIncome, Instant occurredAt) {
    }

    private final ExpenseProjectionRepository expenses;
    private final CategoryProjectionRepository categories;
    private final UserIncomeRepository incomes;

    public ProjectionListener(ExpenseProjectionRepository expenses, CategoryProjectionRepository categories,
            UserIncomeRepository incomes) {
        this.expenses = expenses;
        this.categories = categories;
        this.incomes = incomes;
    }

    @Transactional
    @RabbitListener(queues = EventsConfig.INCOME_QUEUE)
    public void onIncomeEvent(IncomeEvent event) {
        incomes.save(new UserIncomeEntity(event.userId(), event.monthlyIncome() == null ? 0 : event.monthlyIncome()));
    }

    @Transactional
    @RabbitListener(queues = EventsConfig.EXPENSE_QUEUE)
    public void onExpenseEvent(ExpenseEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        if ("expense.deleted".equals(routingKey)) {
            expenses.deleteById(event.expenseId());
            return;
        }
        expenses.save(new ExpenseProjectionEntity(
                event.expenseId(), event.userId(), event.categoryId(), event.categoryPath(),
                Boolean.TRUE.equals(event.categoryMandatory()), event.amount(), event.currency(),
                event.note(), event.expenseDate()));
        log.debug("Projected {} for user {}", routingKey, event.userId());
    }

    @Transactional
    @RabbitListener(queues = EventsConfig.CATEGORY_QUEUE)
    public void onCategoryEvent(CategoryEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        if ("category.deleted".equals(routingKey)) {
            categories.deleteById(event.categoryId());
            return;
        }
        categories.save(new CategoryProjectionEntity(
                event.categoryId(), event.userId(), event.name(), event.parentId(),
                Boolean.TRUE.equals(event.mandatory())));
        // A rename/flag change must reflect in already-projected expenses.
        expenses.refreshCategoryDenormalization(event.categoryId());
    }
}
