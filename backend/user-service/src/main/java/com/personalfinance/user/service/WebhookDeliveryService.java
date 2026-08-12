package com.personalfinance.user.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.personalfinance.user.config.WebhookEventsConfig;
import com.personalfinance.user.entity.WebhookSubscriptionEntity;
import com.personalfinance.user.events.WebhookDeliveryException;
import com.personalfinance.user.events.WebhookDeliveryTask;
import com.personalfinance.user.events.WebhookEndpointClient;
import com.personalfinance.user.events.WebhookSignature;
import com.personalfinance.user.repository.WebhookSubscriptionRepository;

import lombok.RequiredArgsConstructor;

/**
 * One attempt at one delivery, plus the decision about what happens when it
 * fails: park it in the next backoff tier, or give up and dead-letter it.
 *
 * <p>Not transactional on purpose. The HTTP call can take seconds, and holding a
 * database connection open across it would tie the pool to how quickly other
 * people's servers answer.
 */
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {

    public static final String ATTEMPT_HEADER = "x-webhook-attempt";

    /**
     * Consecutive exhausted deliveries before a subscription is switched off. Set
     * well above the retry count so a subscriber having a bad afternoon is not
     * disabled by a single outage — only an endpoint that is reliably gone.
     */
    static final int DISABLE_AFTER_CONSECUTIVE_FAILURES = 10;

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookEndpointClient endpointClient;
    private final RabbitTemplate rabbitTemplate;

    public void deliver(WebhookDeliveryTask task, int attempt) {
        Optional<WebhookSubscriptionEntity> found = subscriptions.findById(task.subscriptionId());
        if (found.isEmpty() || !found.get().isLive()) {
            log.debug("Dropping delivery {} for absent or disabled subscription {}",
                    task.deliveryId(), task.subscriptionId());
            return;
        }

        WebhookSubscriptionEntity subscription = found.get();
        if (succeeded(subscription, task, attempt)) {
            subscription.recordDeliverySucceeded();
            subscriptions.save(subscription);
            return;
        }

        if (attempt <= WebhookEventsConfig.RETRY_BACKOFF_MILLIS.size()) {
            scheduleRetry(task, attempt);
            return;
        }

        abandon(subscription, task);
    }

    private boolean succeeded(WebhookSubscriptionEntity subscription, WebhookDeliveryTask task, int attempt) {
        try {
            int status = endpointClient.post(subscription.getUrl(), task.payload(),
                    headers(subscription, task, attempt));
            if (status >= 200 && status < 300) {
                return true;
            }
            log.warn("Webhook {} answered {} for delivery {} (attempt {})",
                    subscription.getId(), status, task.deliveryId(), attempt);
            return false;
        } catch (WebhookDeliveryException e) {
            log.warn("Webhook {} unreachable for delivery {} (attempt {}): {}",
                    subscription.getId(), task.deliveryId(), attempt, e.getMessage());
            return false;
        }
    }

    private static Map<String, String> headers(WebhookSubscriptionEntity subscription,
            WebhookDeliveryTask task, int attempt) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(WebhookSignature.HEADER, WebhookSignature.sign(subscription.getSecret(), task.payload()));
        headers.put("X-Argali-Event", task.eventType());
        headers.put("X-Argali-Delivery", task.deliveryId().toString());
        headers.put("X-Argali-Attempt", Integer.toString(attempt));
        return headers;
    }

    /**
     * Hands the task to the TTL queue for this attempt. The message leaves the
     * delivery queue immediately, which is what keeps one broken subscriber from
     * stalling everyone else's deliveries.
     */
    private void scheduleRetry(WebhookDeliveryTask task, int attempt) {
        rabbitTemplate.convertAndSend(WebhookEventsConfig.WEBHOOK_EXCHANGE,
                WebhookEventsConfig.retryRoutingKey(attempt), task, withAttempt(attempt + 1));
    }

    private void abandon(WebhookSubscriptionEntity subscription, WebhookDeliveryTask task) {
        rabbitTemplate.convertAndSend(WebhookEventsConfig.WEBHOOK_EXCHANGE,
                WebhookEventsConfig.DEAD_KEY, task);

        boolean justDisabled = subscription.recordDeliveryFailed(DISABLE_AFTER_CONSECUTIVE_FAILURES);
        subscriptions.save(subscription);

        log.error("Webhook {} exhausted retries for delivery {}; dead-lettered{}",
                subscription.getId(), task.deliveryId(),
                justDisabled ? " and subscription disabled" : "");
    }

    private static MessagePostProcessor withAttempt(int attempt) {
        return message -> {
            message.getMessageProperties().setHeader(ATTEMPT_HEADER, attempt);
            return message;
        };
    }

    /** Reads the attempt counter a redelivered message carries, defaulting to the first try. */
    public static int attemptOf(Message message) {
        Object header = message.getMessageProperties().getHeader(ATTEMPT_HEADER);
        return header instanceof Number number ? number.intValue() : 1;
    }
}
