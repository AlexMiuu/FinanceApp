package com.personalfinance.user.events;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.personalfinance.user.config.WebhookEventsConfig;
import com.personalfinance.user.service.WebhookDeliveryService;
import com.personalfinance.user.service.WebhookFanoutService;

import lombok.RequiredArgsConstructor;

/**
 * The two queue ends of webhook delivery.
 *
 * <p>Both handlers swallow their own failures. A webhook that cannot be
 * delivered is a subscriber's problem to fix, not a reason to nack a domain
 * event back onto a queue that would redeliver it forever — the retry path here
 * is the TTL tiers, and it is deliberately the only one.
 */
@Component
@RequiredArgsConstructor
public class WebhookListeners {

    private static final Logger log = LoggerFactory.getLogger(WebhookListeners.class);

    private final WebhookFanoutService fanoutService;
    private final WebhookDeliveryService deliveryService;

    /**
     * Takes the raw message rather than a typed payload: the event shapes differ
     * per service, and forwarding the original bytes is what lets the signature
     * cover exactly what the subscriber receives.
     */
    @RabbitListener(queues = WebhookEventsConfig.FANOUT_QUEUE)
    public void onDomainEvent(Message message,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            fanoutService.fanout(routingKey, new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.error("Webhook fan-out failed for {}", routingKey, e);
        }
    }

    /**
     * The attempt header is absent on the first try and set by each retry hop, so
     * a missing value means this is attempt one.
     */
    @RabbitListener(queues = WebhookEventsConfig.DELIVERY_QUEUE)
    public void onDeliveryTask(WebhookDeliveryTask task,
            @Header(name = WebhookDeliveryService.ATTEMPT_HEADER, required = false) Integer attempt) {
        try {
            deliveryService.deliver(task, attempt == null ? 1 : attempt);
        } catch (RuntimeException e) {
            log.error("Webhook delivery task {} could not be processed", task.deliveryId(), e);
        }
    }
}
