package com.personalfinance.user.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.personalfinance.user.repository.IncomeSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.personalfinance.user.config.EventsConfig;

@Component
public class IncomeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncomeEventPublisher.class);

    private final IncomeSourceRepository incomes;
    private final RabbitTemplate rabbitTemplate;

    public IncomeEventPublisher(IncomeSourceRepository incomes, RabbitTemplate rabbitTemplate) {
        this.incomes = incomes;
        this.rabbitTemplate = rabbitTemplate;
    }

    /** Recomputes normalized monthly income and broadcasts it. */
    public void publishFor(UUID userId) {
        LocalDate today = LocalDate.now();
        long monthlyIncome = incomes.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(i -> !i.getStartDate().isAfter(today))
                .filter(i -> i.getEndDate() == null || !i.getEndDate().isBefore(today))
                .mapToLong(i -> switch (i.getRecurrence()) {
                    case "MONTHLY" -> i.getAmount();
                    case "YEARLY" -> Math.round(i.getAmount() / 12.0);
                    default -> 0;
                })
                .sum();
        try {
            rabbitTemplate.convertAndSend(EventsConfig.EXCHANGE, "income.updated",
                    new IncomeUpdatedEvent(userId, monthlyIncome, Instant.now()));
        } catch (Exception e) {
            log.error("Failed to publish income.updated for {}", userId, e);
        }
    }
}
