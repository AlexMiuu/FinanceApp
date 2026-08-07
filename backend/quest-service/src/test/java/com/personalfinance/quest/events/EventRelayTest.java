package com.personalfinance.quest.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Guards the failure this service shipped without on report-service first: an
 * ApplicationEventPublisher publish that no relay forwards never reaches the
 * broker, and every unit test still passes.
 */
class EventRelayTest {

    @Test
    void erasureCompletionReachesTheExchangeOnItsContractRoutingKey() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        EventRelay relay = new EventRelay(rabbit);
        Events.ErasureCompleted event = new Events.ErasureCompleted(
                UUID.randomUUID(), UUID.randomUUID(), "quest", Instant.now());

        relay.relay(event);

        verify(rabbit).convertAndSend("pf.events", "user.erasure.completed", (Object) event);
    }

    @Test
    void aQuestTransitionIsRoutedByItsNewStatus() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        EventRelay relay = new EventRelay(rabbit);
        Events.QuestChanged event = new Events.QuestChanged(
                UUID.randomUUID(), UUID.randomUUID(), "Keep it under 100 RON", "FAILED", Instant.now());

        relay.relay(event);

        verify(rabbit).convertAndSend("pf.events", "quest.failed", (Object) event);
    }

    @Test
    void aBrokerOutageIsLoggedRatherThanRolledBackOntoTheCaller() {
        // Given the broker is unreachable
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new RuntimeException("broker down"))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));
        EventRelay relay = new EventRelay(rabbit);
        Events.ErasureCompleted event = new Events.ErasureCompleted(
                UUID.randomUUID(), UUID.randomUUID(), "quest", Instant.now());

        // When an event is relayed after its transaction has already committed
        // Then nothing propagates — the commit stands and the failure is logged
        relay.relay(event);

        verify(rabbit).convertAndSend("pf.events", "user.erasure.completed", (Object) event);
    }
}
