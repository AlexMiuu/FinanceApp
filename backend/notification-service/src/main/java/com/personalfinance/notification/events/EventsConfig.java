package com.personalfinance.notification.events;

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

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class EventsConfig {

    public static final String EXCHANGE = "pf.events";
    public static final String QUEST_QUEUE = "notification-service.quest-events";
    public static final String WEATHER_QUEUE = "notification-service.weather-events";
    public static final String ERASURE_REQUESTED_QUEUE = "notification-service.user-erasure-requested";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue questQueue() {
        return new Queue(QUEST_QUEUE, true);
    }

    @Bean
    Binding questBinding(Queue questQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(questQueue).to(eventsExchange).with("quest.*");
    }

    @Bean
    Queue weatherQueue() {
        return new Queue(WEATHER_QUEUE, true);
    }

    @Bean
    Binding weatherBinding(Queue weatherQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(weatherQueue).to(eventsExchange).with("ambient.weather.updated");
    }

    @Bean
    Queue erasureRequestedQueue() {
        return new Queue(ERASURE_REQUESTED_QUEUE, true);
    }

    @Bean
    Binding erasureRequestedBinding(Queue erasureRequestedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(erasureRequestedQueue).to(eventsExchange).with("user.erasure.requested");
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    /**
     * Declared explicitly so outbound events serialize as JSON. Boot's
     * auto-configured RabbitTemplate would use the default SimpleMessageConverter
     * and publish Java-serialized bodies no other service can read.
     */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
