package com.personalfinance.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.user.config.WebhookEventsConfig;
import com.personalfinance.user.entity.WebhookSubscriptionEntity;
import com.personalfinance.user.events.WebhookDeliveryTask;
import com.personalfinance.user.repository.WebhookSubscriptionRepository;

class WebhookFanoutServiceTest {

    private final UUID userId = UUID.randomUUID();

    private WebhookSubscriptionRepository subscriptions;
    private RabbitTemplate rabbitTemplate;
    private WebhookFanoutService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(WebhookSubscriptionRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        service = new WebhookFanoutService(subscriptions, rabbitTemplate, new ObjectMapper());
    }

    @Test
    void aPayloadWithNoUserIdFieldIsSkippedWithNoRabbitSend() {
        service.fanout("expense.created", "{\"amount\":10}");

        verifyNoInteractions(subscriptions);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void aPayloadWithANonTextualUserIdIsSkippedWithNoRabbitSend() {
        service.fanout("expense.created", "{\"userId\":123}");

        verifyNoInteractions(subscriptions);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void aPayloadWithAMalformedUserIdIsSkippedWithNoRabbitSend() {
        service.fanout("expense.created", "{\"userId\":\"not-a-uuid\"}");

        verifyNoInteractions(subscriptions);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void fansOutOneDeliveryTaskPerMatchingNonDisabledSubscriptionForThatUser() {
        WebhookSubscriptionEntity matching =
                new WebhookSubscriptionEntity(userId, "https://example.com/a", "expense.*", "secret-a");
        WebhookSubscriptionEntity alsoMatching =
                new WebhookSubscriptionEntity(userId, "https://example.com/b", "expense.#", "secret-b");
        when(subscriptions.findByUserIdAndDisabledAtIsNull(userId)).thenReturn(List.of(matching, alsoMatching));

        service.fanout("expense.created", "{\"userId\":\"" + userId + "\"}");

        verify(rabbitTemplate, times(2)).convertAndSend(
                org.mockito.ArgumentMatchers.eq(WebhookEventsConfig.WEBHOOK_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(WebhookEventsConfig.DELIVERY_KEY),
                any(WebhookDeliveryTask.class));
    }

    @Test
    void aSubscriptionForADifferentRoutingKeyPatternIsNotIncluded() {
        WebhookSubscriptionEntity nonMatching =
                new WebhookSubscriptionEntity(userId, "https://example.com/a", "income.updated", "secret-a");
        when(subscriptions.findByUserIdAndDisabledAtIsNull(userId)).thenReturn(List.of(nonMatching));

        service.fanout("expense.created", "{\"userId\":\"" + userId + "\"}");

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(WebhookDeliveryTask.class));
    }
}
