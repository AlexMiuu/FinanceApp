package com.personalfinance.quest.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays domain events to RabbitMQ after the owning transaction commits.
 * Mirrors expense-service's and report-service's EventRelay.
 *
 * <p>Without this component an ApplicationEventPublisher publish stays inside
 * the application context and never reaches the broker — a failure mode unit
 * tests cannot see, because they assert on the published object rather than on
 * the wire.
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
