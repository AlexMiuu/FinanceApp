package com.personalfinance.user.events;

import java.util.Map;

/**
 * The one call that leaves the process during a delivery. Extracted behind an
 * interface so retry, backoff, and disabling can be exercised in tests without a
 * real endpoint on the other end.
 */
public interface WebhookEndpointClient {

    /**
     * @return the subscriber's HTTP status code
     * @throws WebhookDeliveryException when the endpoint could not be reached at all
     */
    int post(String url, String payload, Map<String, String> headers);
}
