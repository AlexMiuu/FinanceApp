package com.personalfinance.notification.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.personalfinance.notification.service.NotificationService;

/** Persists oath.kept / oath.slipped / oath.forgone as notifications (M12). */
@Component
public class OathEventListener {

    private static final Logger log = LoggerFactory.getLogger(OathEventListener.class);

    private final NotificationService notificationService;

    public OathEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Caught, not rethrown, for the same reason as {@link QuestEventListener}: this queue has no DLQ. */
    @RabbitListener(queues = EventsConfig.OATH_QUEUE)
    public void onOathEvent(Events.OathEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        try {
            notificationService.recordOathNotification(event.userId(), routingKey, event.title(), event.oathId());
        } catch (Exception e) {
            log.error("Failed to record notification for {} (user {})", routingKey, event.userId(), e);
        }
    }
}
