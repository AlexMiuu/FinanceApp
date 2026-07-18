package com.personalfinance.quest.events;

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

import com.personalfinance.quest.domain.CategoryProjectionEntity;
import com.personalfinance.quest.domain.CategoryProjectionRepository;
import com.personalfinance.quest.domain.ExpenseProjectionEntity;
import com.personalfinance.quest.domain.ExpenseProjectionRepository;
import com.personalfinance.quest.quest.QuestService;
import com.personalfinance.quest.quest.UserIncomeEntity;
import com.personalfinance.quest.quest.UserIncomeRepository;

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
    private final QuestService questService;

    public ProjectionListener(ExpenseProjectionRepository expenses, CategoryProjectionRepository categories,
            UserIncomeRepository incomes, QuestService questService) {
        this.expenses = expenses;
        this.categories = categories;
        this.incomes = incomes;
        this.questService = questService;
    }

    @Transactional
    @RabbitListener(queues = EventsConfig.EXPENSE_QUEUE)
    public void onExpenseEvent(ExpenseEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        if ("expense.deleted".equals(routingKey)) {
            expenses.deleteById(event.expenseId());
        } else {
            expenses.save(new ExpenseProjectionEntity(
                    event.expenseId(), event.userId(), event.categoryId(), event.categoryPath(),
                    Boolean.TRUE.equals(event.categoryMandatory()), event.amount(), event.currency(),
                    event.note(), event.expenseDate()));
        }
        // Every spend can complete or fail an active quest right away (FR-11).
        questService.refreshActiveQuests(event.userId(), LocalDate.now());
        log.debug("Projected {} for user {}", routingKey, event.userId());
    }

    @Transactional
    @RabbitListener(queues = EventsConfig.INCOME_QUEUE)
    public void onIncomeEvent(IncomeEvent event) {
        incomes.save(new UserIncomeEntity(event.userId(),
                event.monthlyIncome() == null ? 0 : event.monthlyIncome()));
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
