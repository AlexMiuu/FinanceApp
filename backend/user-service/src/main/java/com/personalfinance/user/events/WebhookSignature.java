package com.personalfinance.user.events;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 over the exact bytes delivered, so a subscriber can recompute the
 * signature from the raw request body and be sure the payload is both untampered
 * and genuinely from this deployment.
 */
public final class WebhookSignature {

    public static final String HEADER = "X-Argali-Signature";

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignature() {
    }

    public static String sign(String secret, String payload) {
        if (secret == null || payload == null) {
            throw new IllegalArgumentException("Webhook signing needs both a secret and a payload");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return "sha256=" + toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
