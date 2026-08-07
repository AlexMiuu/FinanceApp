package com.personalfinance.quest.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.personalfinance.quest.service.PrivacyService;

@Component
public class UserErasureRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(UserErasureRequestedListener.class);

    private final PrivacyService privacyService;

    public UserErasureRequestedListener(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    /**
     * Caught, not rethrown: with no DLQ configured, a rejected message would
     * requeue and redeliver immediately (Spring AMQP's default), looping
     * forever on the same failure and blocking every other user's erasure
     * behind it on this single queue. Logging loudly and returning normally
     * acks the message and leaves the erasure request honestly PENDING —
     * mirrors expense-service's and report-service's listener.
     */
    @RabbitListener(queues = EventsConfig.ERASURE_REQUESTED_QUEUE)
    public void onUserErasureRequested(Events.UserErasureRequested event) {
        log.info("Erasing quest data for user {}", event.userId());
        try {
            privacyService.eraseUserData(event.erasureRequestId(), event.userId());
        } catch (Exception e) {
            log.error("Failed to erase quest data for user {} (erasureRequestId {}) — erasure request stays PENDING",
                    event.userId(), event.erasureRequestId(), e);
        }
    }
}
