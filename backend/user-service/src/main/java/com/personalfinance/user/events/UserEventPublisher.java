package com.personalfinance.user.events;

import com.personalfinance.user.config.EventsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays domain events to RabbitMQ after the owning transaction commits, so
 * consumers never see events for rolled-back writes. (A durable outbox table
 * can replace this if delivery guarantees ever need to be stronger.)
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            rabbitTemplate.convertAndSend(EventsConfig.EXCHANGE, "user.registered", event);
        } catch (Exception e) {
            // Losing this event only means default categories are seeded lazily
            // instead; never fail the registration for it.
            log.error("Failed to publish user.registered for {}", event.userId(), e);
        }
    }
}
