package com.personalfinance.user.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology for webhook fan-out, kept separate from {@link EventsConfig} so the
 * first-party event wiring stays readable.
 *
 * <p>Two hops. The fan-out queue consumes domain events off {@code pf.events} and
 * turns one event into one delivery task per matching subscription; the delivery
 * queue then owns the HTTP attempt. Splitting them means a subscriber that is
 * slow or broken cannot hold up the domain event stream every other subscriber
 * is fed from.
 *
 * <p>Retries are staged through TTL queues that have no consumer: a failed
 * delivery is republished into the tier matching its attempt number, sits there
 * until the TTL expires, and is then dead-lettered back onto the delivery queue.
 * The wait therefore costs nothing but broker storage, and no consumer thread is
 * ever parked on a sleep. One queue per tier rather than a per-message TTL on a
 * shared queue, because RabbitMQ only expires messages from the head of a queue —
 * a 25s message in front would otherwise hold back a 1s one behind it.
 */
@Configuration
public class WebhookEventsConfig {

    public static final String WEBHOOK_EXCHANGE = "pf.webhooks";
    public static final String FANOUT_QUEUE = "user-service.webhook-fanout";
    public static final String DELIVERY_QUEUE = "user-service.webhook-delivery";
    public static final String DEAD_QUEUE = "user-service.webhook-dead";

    public static final String DELIVERY_KEY = "delivery";
    public static final String DEAD_KEY = "dead";

    /**
     * Backoff before each redelivery, in milliseconds. The list length is the
     * number of retries after the first attempt, so a delivery is tried at most
     * {@code RETRY_BACKOFF_MILLIS.size() + 1} times before it is dead-lettered.
     */
    public static final List<Integer> RETRY_BACKOFF_MILLIS = List.of(1_000, 5_000, 25_000);

    /**
     * Domain events offered to subscribers. Deliberately enumerated rather than
     * bound with {@code #}: {@code user.registered} and the {@code user.erasure.*}
     * pair are internal lifecycle and privacy signals, and forwarding those to an
     * arbitrary third-party URL is not something a subscription should be able to
     * opt into.
     */
    private static final List<String> SUBSCRIBABLE_EVENTS = List.of(
            "expense.*", "category.*", "income.updated",
            "quest.*", "oath.*", "ambient.weather.updated");

    public static String retryQueueName(int attempt) {
        return "user-service.webhook-retry-" + attempt;
    }

    public static String retryRoutingKey(int attempt) {
        return "retry." + attempt;
    }

    @Bean
    DirectExchange webhookExchange() {
        return new DirectExchange(WEBHOOK_EXCHANGE, true, false);
    }

    @Bean
    Queue webhookFanoutQueue() {
        return new Queue(FANOUT_QUEUE, true);
    }

    @Bean
    Declarables webhookFanoutBindings(Queue webhookFanoutQueue, TopicExchange eventsExchange) {
        return new Declarables(SUBSCRIBABLE_EVENTS.stream()
                .<Declarable>map(key -> BindingBuilder.bind(webhookFanoutQueue).to(eventsExchange).with(key))
                .toList());
    }

    @Bean
    Queue webhookDeliveryQueue() {
        return new Queue(DELIVERY_QUEUE, true);
    }

    @Bean
    Binding webhookDeliveryBinding(Queue webhookDeliveryQueue, DirectExchange webhookExchange) {
        return BindingBuilder.bind(webhookDeliveryQueue).to(webhookExchange).with(DELIVERY_KEY);
    }

    /**
     * One parked queue per backoff tier. Nothing consumes these; the TTL expiring
     * is what moves a message back onto the delivery queue.
     */
    @Bean
    Declarables webhookRetryTiers(DirectExchange webhookExchange) {
        List<Declarable> declarables = new ArrayList<>();
        for (int attempt = 1; attempt <= RETRY_BACKOFF_MILLIS.size(); attempt++) {
            Queue tier = QueueBuilder.durable(retryQueueName(attempt))
                    .withArguments(Map.of(
                            "x-message-ttl", RETRY_BACKOFF_MILLIS.get(attempt - 1),
                            "x-dead-letter-exchange", WEBHOOK_EXCHANGE,
                            "x-dead-letter-routing-key", DELIVERY_KEY))
                    .build();
            declarables.add(tier);
            declarables.add(BindingBuilder.bind(tier).to(webhookExchange).with(retryRoutingKey(attempt)));
        }
        return new Declarables(declarables);
    }

    @Bean
    Queue webhookDeadQueue() {
        return new Queue(DEAD_QUEUE, true);
    }

    @Bean
    Binding webhookDeadBinding(Queue webhookDeadQueue, DirectExchange webhookExchange) {
        return BindingBuilder.bind(webhookDeadQueue).to(webhookExchange).with(DEAD_KEY);
    }
}
