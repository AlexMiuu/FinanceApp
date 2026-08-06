package com.personalfinance.user.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All services publish to the shared topic exchange (DESIGN.md section 6,
 * event catalog). Routing keys follow "<entity>.<action>", e.g. "user.registered".
 */
@Configuration
public class EventsConfig {

    public static final String EXCHANGE = "pf.events";
    public static final String ERASURE_COMPLETED_QUEUE = "user-service.erasure-completed";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue erasureCompletedQueue() {
        return new Queue(ERASURE_COMPLETED_QUEUE, true);
    }

    @Bean
    Binding erasureCompletedBinding(Queue erasureCompletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(erasureCompletedQueue).to(eventsExchange).with("user.erasure.completed");
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        // Spring's ObjectMapper, not a fresh one: java.time types need JavaTimeModule.
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        // Deserialize into the @RabbitListener parameter type instead of the
        // publisher's __TypeId__ class (which lives in another service).
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
