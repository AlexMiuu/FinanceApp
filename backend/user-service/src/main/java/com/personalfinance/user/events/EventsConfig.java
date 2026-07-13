package com.personalfinance.user.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        // Spring's ObjectMapper, not a fresh one: java.time types need JavaTimeModule.
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
