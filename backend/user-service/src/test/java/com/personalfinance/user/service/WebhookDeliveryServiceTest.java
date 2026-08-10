package com.personalfinance.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.personalfinance.user.config.WebhookEventsConfig;
import com.personalfinance.user.entity.WebhookSubscriptionEntity;
import com.personalfinance.user.events.WebhookDeliveryException;
import com.personalfinance.user.events.WebhookDeliveryTask;
import com.personalfinance.user.events.WebhookEndpointClient;
import com.personalfinance.user.events.WebhookSignature;
import com.personalfinance.user.repository.WebhookSubscriptionRepository;

/**
 * Covers the M14 roadmap exit criterion: a webhook returning 500 retries with
 * backoff, then dead-letters after exhausting retries, without blocking the
 * queue (each attempt is a single message send, never a blocking sleep).
 */
class WebhookDeliveryServiceTest {

    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private WebhookSubscriptionRepository subscriptions;
    private WebhookEndpointClient endpointClient;
    private RabbitTemplate rabbitTemplate;
    private WebhookDeliveryService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(WebhookSubscriptionRepository.class);
        endpointClient = mock(WebhookEndpointClient.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new WebhookDeliveryService(subscriptions, endpointClient, rabbitTemplate);
    }

    private WebhookSubscriptionEntity liveSubscription() {
        WebhookSubscriptionEntity entity =
                new WebhookSubscriptionEntity(userId, "https://example.com/hook", "expense.*", "shh-secret");
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(entity));
        return entity;
    }

    private WebhookDeliveryTask task() {
        return new WebhookDeliveryTask(subscriptionId, "expense.created", "{\"userId\":\"" + userId + "\"}",
                UUID.randomUUID());
    }

    @Test
    void a2xxResponseMarksDeliverySucceededAndResetsConsecutiveFailures() {
        WebhookSubscriptionEntity subscription = liveSubscription();
        subscription.recordDeliveryFailed(999);
        assertThat(subscription.getConsecutiveFailures()).isEqualTo(1);
        when(endpointClient.post(anyString(), anyString(), anyMap())).thenReturn(200);

        service.deliver(task(), 1);

        assertThat(subscription.getConsecutiveFailures()).isEqualTo(0);
        verify(subscriptions).save(subscription);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(WebhookDeliveryTask.class));
    }

    @Test
    void aNon2xxResponseOnAnEarlyAttemptSchedulesARetryAtTheNextBackoffTier() {
        liveSubscription();
        when(endpointClient.post(anyString(), anyString(), anyMap())).thenReturn(500);
        WebhookDeliveryTask task = task();

        service.deliver(task, 1);

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(WebhookEventsConfig.WEBHOOK_EXCHANGE),
                eq(WebhookEventsConfig.retryRoutingKey(1)), eq(task), processor.capture());
        assertThat(processor.getValue()).isNotNull();
        verify(subscriptions, never()).save(any());
    }

    @Test
    void aThrownDeliveryExceptionOnAnEarlyAttemptSchedulesARetry() {
        liveSubscription();
        when(endpointClient.post(anyString(), anyString(), anyMap()))
                .thenThrow(new WebhookDeliveryException("connection refused", null));
        WebhookDeliveryTask task = task();

        service.deliver(task, 2);

        verify(rabbitTemplate).convertAndSend(eq(WebhookEventsConfig.WEBHOOK_EXCHANGE),
                eq(WebhookEventsConfig.retryRoutingKey(2)), eq(task), any(MessagePostProcessor.class));
    }

    @Test
    void aFailureOnTheLastRetryAttemptStillSchedulesARetryNotADeadLetter() {
        liveSubscription();
        when(endpointClient.post(anyString(), anyString(), anyMap())).thenReturn(503);
        WebhookDeliveryTask task = task();
        int lastRetryAttempt = WebhookEventsConfig.RETRY_BACKOFF_MILLIS.size();

        service.deliver(task, lastRetryAttempt);

        verify(rabbitTemplate).convertAndSend(eq(WebhookEventsConfig.WEBHOOK_EXCHANGE),
                eq(WebhookEventsConfig.retryRoutingKey(lastRetryAttempt)), eq(task), any(MessagePostProcessor.class));
    }

    @Test
    void aFailureBeyondTheRetryBudgetDeadLettersAndRecordsTheFailure() {
        WebhookSubscriptionEntity subscription = liveSubscription();
        when(endpointClient.post(anyString(), anyString(), anyMap())).thenReturn(500);
        WebhookDeliveryTask task = task();
        int deadLetterAttempt = WebhookEventsConfig.RETRY_BACKOFF_MILLIS.size() + 1;

        service.deliver(task, deadLetterAttempt);

        verify(rabbitTemplate).convertAndSend(WebhookEventsConfig.WEBHOOK_EXCHANGE, WebhookEventsConfig.DEAD_KEY, task);
        assertThat(subscription.getConsecutiveFailures()).isEqualTo(1);
        verify(subscriptions).save(subscription);
    }

    @Test
    void aSubscriptionThatIsAlreadyDisabledIsDroppedWithoutHttpCallOrRabbitSend() {
        WebhookSubscriptionEntity entity =
                new WebhookSubscriptionEntity(userId, "https://example.com/hook", "expense.*", "shh-secret");
        entity.recordDeliveryFailed(1);
        assertThat(entity.isLive()).isFalse();
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.of(entity));

        service.deliver(task(), 1);

        verifyNoInteractions(endpointClient);
        verifyNoInteractions(rabbitTemplate);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void aMissingSubscriptionIsDroppedWithoutHttpCallOrRabbitSend() {
        when(subscriptions.findById(subscriptionId)).thenReturn(Optional.empty());

        service.deliver(task(), 1);

        verifyNoInteractions(endpointClient);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void theSignatureAndAttemptHeadersAreSetOnTheOutboundCall() {
        WebhookSubscriptionEntity subscription = liveSubscription();
        when(endpointClient.post(anyString(), anyString(), anyMap())).thenReturn(200);
        WebhookDeliveryTask task = task();

        service.deliver(task, 3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(endpointClient).post(eq(subscription.getUrl()), eq(task.payload()), headers.capture());
        assertThat(headers.getValue())
                .containsEntry(WebhookSignature.HEADER, WebhookSignature.sign(subscription.getSecret(), task.payload()))
                .containsEntry("X-Argali-Attempt", "3");
    }
}
