package com.personalfinance.quest.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class EventsConfig {

    public static final String EXCHANGE = "pf.events";
    public static final String EXPENSE_QUEUE = "quest-service.expense-events";
    public static final String CATEGORY_QUEUE = "quest-service.category-events";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue expenseQueue() {
        return new Queue(EXPENSE_QUEUE, true);
    }

    @Bean
    Binding expenseBinding(Queue expenseQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(expenseQueue).to(eventsExchange).with("expense.*");
    }

    @Bean
    Queue categoryQueue() {
        return new Queue(CATEGORY_QUEUE, true);
    }

    @Bean
    Binding categoryBinding(Queue categoryQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(categoryQueue).to(eventsExchange).with("category.*");
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
