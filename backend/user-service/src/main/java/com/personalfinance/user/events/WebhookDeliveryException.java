package com.personalfinance.user.events;

/** The subscriber's endpoint could not be reached, as distinct from answering badly. */
public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
