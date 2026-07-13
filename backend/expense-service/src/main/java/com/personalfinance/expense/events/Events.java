package com.personalfinance.expense.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain events published to the pf.events exchange (DESIGN.md event catalog).
 * Expense events carry a denormalized category snapshot so report/quest
 * projections never have to call back into this service.
 */
public final class Events {

    private Events() {
    }

    public sealed interface DomainEvent permits ExpenseChanged, ExpenseDeleted, CategoryChanged, CategoryDeleted {
        String routingKey();
    }

    public record ExpenseChanged(
            UUID expenseId,
            UUID userId,
            UUID categoryId,
            String categoryPath,
            boolean categoryMandatory,   // effective: the category or its parent is mandatory
            long amount,
            String currency,
            String note,
            LocalDate expenseDate,
            Instant occurredAt,
            boolean created) implements DomainEvent {

        @Override
        public String routingKey() {
            return created ? "expense.created" : "expense.updated";
        }
    }

    public record ExpenseDeleted(UUID expenseId, UUID userId, Instant occurredAt) implements DomainEvent {

        @Override
        public String routingKey() {
            return "expense.deleted";
        }
    }

    public record CategoryChanged(
            UUID categoryId,
            UUID userId,
            String name,
            UUID parentId,
            boolean mandatory,
            Instant occurredAt) implements DomainEvent {

        @Override
        public String routingKey() {
            return "category.updated";
        }
    }

    public record CategoryDeleted(UUID categoryId, UUID userId, Instant occurredAt) implements DomainEvent {

        @Override
        public String routingKey() {
            return "category.deleted";
        }
    }

    /** Mirror of user-service's user.registered payload (consumed here). */
    public record UserRegistered(UUID userId, String email, String displayName, Instant occurredAt) {
    }
}
