package com.personalfinance.user.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.user.config.WebhookEventsConfig;
import com.personalfinance.user.events.WebhookDeliveryTask;
import com.personalfinance.user.events.WebhookEventPattern;
import com.personalfinance.user.repository.WebhookSubscriptionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns one domain event into one delivery task per subscription that asked for
 * it. Every event this service publishes carries the subscriber's own user id,
 * which is what keeps a webhook scoped to its owner's data.
 */
@Service
@RequiredArgsConstructor
public class WebhookFanoutService {

    private static final Logger log = LoggerFactory.getLogger(WebhookFanoutService.class);

    private final WebhookSubscriptionRepository subscriptions;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void fanout(String routingKey, String payload) {
        Optional<UUID> owner = ownerOf(payload);
        if (owner.isEmpty()) {
            log.warn("Skipping webhook fan-out for {}: payload carries no usable userId", routingKey);
            return;
        }

        subscriptions.findByUserIdAndDisabledAtIsNull(owner.get()).stream()
                .filter(subscription ->
                        WebhookEventPattern.matches(subscription.getEventPattern(), routingKey))
                .forEach(subscription -> rabbitTemplate.convertAndSend(
                        WebhookEventsConfig.WEBHOOK_EXCHANGE,
                        WebhookEventsConfig.DELIVERY_KEY,
                        new WebhookDeliveryTask(subscription.getId(), routingKey, payload, UUID.randomUUID())));
    }

    private Optional<UUID> ownerOf(String payload) {
        try {
            JsonNode userId = objectMapper.readTree(payload).get("userId");
            if (userId == null || !userId.isTextual()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(userId.asText()));
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
