package com.personalfinance.user.events;

import java.util.UUID;

/**
 * One event owed to one subscriber. The subscription's url and secret are
 * deliberately not carried here and are re-read at delivery time, so a
 * subscription deleted while a retry is parked stops delivering instead of
 * draining against stale settings.
 *
 * @param payload the original event JSON, forwarded verbatim so the signature
 *                covers exactly the bytes the subscriber receives
 */
public record WebhookDeliveryTask(
        UUID subscriptionId,
        String eventType,
        String payload,
        UUID deliveryId) {
}
