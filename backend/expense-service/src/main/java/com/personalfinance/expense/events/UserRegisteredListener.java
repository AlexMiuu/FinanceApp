package com.personalfinance.expense.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.personalfinance.expense.service.CategoryService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final CategoryService categoryService;

    @RabbitListener(queues = EventsConfig.USER_REGISTERED_QUEUE)
    public void onUserRegistered(Events.UserRegistered event) {
        log.info("Seeding default categories for new user {}", event.userId());
        categoryService.seedDefaults(event.userId());
    }
}
