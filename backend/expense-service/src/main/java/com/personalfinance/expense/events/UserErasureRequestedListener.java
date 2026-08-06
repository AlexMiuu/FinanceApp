package com.personalfinance.expense.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.personalfinance.expense.service.PrivacyService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserErasureRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(UserErasureRequestedListener.class);

    private final PrivacyService privacyService;

    /**
     * Caught, not rethrown: with no DLQ configured, a rejected message would
     * requeue and redeliver immediately (Spring AMQP's default), looping
     * forever on the same failure and blocking every other user's erasure
     * behind it on this single queue. Logging loudly and returning normally
     * acks the message and leaves the erasure request honestly PENDING —
     * the same "don't claim completion that didn't happen" reasoning as the
     * expected-services list.
     */
    @RabbitListener(queues = EventsConfig.ERASURE_REQUESTED_QUEUE)
    public void onUserErasureRequested(Events.UserErasureRequested event) {
        log.info("Erasing expense data for user {}", event.userId());
        try {
            privacyService.eraseUserData(event.erasureRequestId(), event.userId());
        } catch (Exception e) {
            log.error("Failed to erase expense data for user {} (erasureRequestId {}) — erasure request stays PENDING",
                    event.userId(), event.erasureRequestId(), e);
        }
    }
}
