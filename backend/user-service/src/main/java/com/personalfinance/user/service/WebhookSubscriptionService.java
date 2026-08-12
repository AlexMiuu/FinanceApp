package com.personalfinance.user.service;

import static com.personalfinance.user.service.RequestGuards.requireBody;
import static com.personalfinance.user.service.RequestGuards.requireFound;
import static com.personalfinance.user.service.RequestGuards.requireId;
import static com.personalfinance.user.service.RequestGuards.requireUser;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.user.dto.WebhookCreateRequestDto;
import com.personalfinance.user.dto.WebhookCreatedDto;
import com.personalfinance.user.dto.WebhookDto;
import com.personalfinance.user.entity.WebhookSubscriptionEntity;
import com.personalfinance.user.events.WebhookEventPattern;
import com.personalfinance.user.repository.WebhookSubscriptionRepository;

import lombok.RequiredArgsConstructor;

/** Registration and lifecycle of a user's webhook endpoints. */
@Service
@RequiredArgsConstructor
public class WebhookSubscriptionService {

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository subscriptions;

    @Transactional
    public WebhookCreatedDto create(UUID userId, WebhookCreateRequestDto request) {
        requireUser(userId);
        requireBody(request);

        String url = requireDeliverableUrl(request.url());
        String pattern = requireSubscribablePattern(request.eventPattern());
        String secret = generateSecret();

        WebhookSubscriptionEntity saved =
                subscriptions.save(new WebhookSubscriptionEntity(userId, url, pattern, secret));

        return new WebhookCreatedDto(saved.getId(), saved.getUrl(), saved.getEventPattern(),
                saved.getCreatedAt(), secret);
    }

    @Transactional(readOnly = true)
    public List<WebhookDto> list(UUID userId) {
        requireUser(userId);

        return subscriptions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(WebhookSubscriptionService::toDto)
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        requireUser(userId);
        requireId(id, "Webhook");

        WebhookSubscriptionEntity subscription = requireFound(
                subscriptions.findByIdAndUserId(id, userId), "Webhook subscription not found");
        subscriptions.delete(subscription);
    }

    private static WebhookDto toDto(WebhookSubscriptionEntity entity) {
        return new WebhookDto(entity.getId(), entity.getUrl(), entity.getEventPattern(),
                entity.getCreatedAt(), entity.getDisabledAt(), entity.getConsecutiveFailures());
    }

    private static String generateSecret() {
        byte[] material = new byte[SECRET_BYTES];
        RANDOM.nextBytes(material);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(material);
    }

    private static String requireSubscribablePattern(String pattern) {
        if (!WebhookEventPattern.isValid(pattern)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Event pattern must be dot-separated words, optionally using * or #");
        }
        return pattern;
    }

    /**
     * The delivery worker fetches whatever URL is stored here, so an unchecked
     * value would turn this endpoint into a request forwarder pointed at the
     * private network the services themselves sit on. Literal loopback and
     * RFC1918 hosts are refused for that reason.
     *
     * <p>This stops the obvious cases, not a host that resolves to a private
     * address only at delivery time; closing that properly needs egress
     * filtering rather than input validation.
     */
    private static String requireDeliverableUrl(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook url is not a valid URI");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook url must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook url must include a host");
        }
        if (isPrivateHost(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Webhook url must not point at a private or loopback address");
        }

        return uri.toString();
    }

    private static boolean isPrivateHost(String host) {
        String candidate = host.toLowerCase(Locale.ROOT);
        if (candidate.equals("localhost") || candidate.endsWith(".localhost") || candidate.equals("[::1]")) {
            return true;
        }
        return candidate.startsWith("127.")
                || candidate.startsWith("10.")
                || candidate.startsWith("192.168.")
                || candidate.startsWith("169.254.")
                || candidate.matches("^172\\.(1[6-9]|2\\d|3[01])\\..*");
    }
}
