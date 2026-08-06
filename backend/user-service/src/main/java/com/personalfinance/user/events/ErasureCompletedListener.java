package com.personalfinance.user.events;

import java.time.Instant;
import java.util.UUID;

import com.personalfinance.user.config.EventsConfig;
import com.personalfinance.user.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Collects the per-service acknowledgements that close out an erasure. User
 * Service owns the terminal state, so it consumes its own fanout back.
 */
@Component
@RequiredArgsConstructor
public class ErasureCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(ErasureCompletedListener.class);

    /** Mirror of the ack every service publishes once it has deleted its copy. */
    public record ErasureCompletedEvent(UUID erasureRequestId, UUID userId, String service, Instant occurredAt) {
    }

    private final AccountDeletionService accountDeletionService;

    /**
     * Caught, not rethrown: no DLQ is configured, so a rejected message would
     * requeue and redeliver immediately, looping forever on the same failure.
     * recordServiceAck already tolerates malformed/unknown input by logging
     * and returning; this guards against anything else it might throw (a
     * transient DB error, for instance) so one bad ack can't wedge the queue
     * for every other user's completion events behind it.
     */
    @RabbitListener(queues = EventsConfig.ERASURE_COMPLETED_QUEUE)
    public void onErasureCompleted(ErasureCompletedEvent event) {
        try {
            accountDeletionService.recordServiceAck(event.erasureRequestId(), event.service());
        } catch (Exception e) {
            log.error("Failed to record erasure ack for request {} from service {}",
                    event.erasureRequestId(), event.service(), e);
        }
    }
}
