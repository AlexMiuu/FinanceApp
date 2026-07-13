package com.personalfinance.expense.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays domain events to RabbitMQ after the owning transaction commits.
 * Consumers rebuild projections from these, so a lost event self-heals on the
 * next change to the same row; a durable outbox can replace this if needed.
 */
@Component
public class EventRelay {

    private static final Logger log = LoggerFactory.getLogger(EventRelay.class);

    private final RabbitTemplate rabbitTemplate;

    public EventRelay(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener
    public void relay(Events.DomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(EventsConfig.EXCHANGE, event.routingKey(), event);
        } catch (Exception e) {
            log.error("Failed to publish {}", event.routingKey(), e);
        }
    }
}
