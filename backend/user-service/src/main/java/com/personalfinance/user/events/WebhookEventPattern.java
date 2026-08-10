package com.personalfinance.user.events;

import java.util.regex.Pattern;

/**
 * Matches a stored subscription pattern against an event's routing key using
 * AMQP topic semantics, so a pattern a user writes behaves the way the same
 * string would behave as a queue binding: {@code *} is exactly one word,
 * {@code #} is zero or more.
 *
 * <p>The matching happens here rather than as one broker binding per
 * subscription because bindings are global infrastructure — a user editing their
 * own webhooks would otherwise be reconfiguring the exchange topology.
 */
public final class WebhookEventPattern {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_*#]+(\\.[a-zA-Z0-9_*#]+)*$");

    private WebhookEventPattern() {
    }

    public static boolean isValid(String pattern) {
        return pattern != null && !pattern.isBlank() && VALID_PATTERN.matcher(pattern).matches();
    }

    public static boolean matches(String pattern, String routingKey) {
        if (pattern == null || routingKey == null) {
            return false;
        }
        return Pattern.compile(toRegex(pattern)).matcher(routingKey).matches();
    }

    private static String toRegex(String pattern) {
        String[] segments = pattern.split("\\.", -1);
        StringBuilder regex = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            boolean first = i == 0;

            if ("#".equals(segment)) {
                // Absorbs the separator in front of it so that "expense.#" still
                // matches the bare word "expense".
                regex.append(first ? "(?:[^.]+(?:\\.[^.]+)*)?" : "(?:\\.[^.]+)*");
                continue;
            }

            if (!first) {
                regex.append("\\.");
            }
            regex.append("*".equals(segment) ? "[^.]+" : Pattern.quote(segment));
        }

        return regex.toString();
    }
}
