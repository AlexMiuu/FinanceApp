package com.personalfinance.user.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    @Test
    void signIsDeterministicForTheSameSecretAndPayload() {
        String first = WebhookSignature.sign("secret", "{\"a\":1}");
        String second = WebhookSignature.sign("secret", "{\"a\":1}");

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("sha256=");
    }

    @Test
    void signProducesADifferentSignatureForADifferentSecret() {
        String first = WebhookSignature.sign("secret-a", "{\"a\":1}");
        String second = WebhookSignature.sign("secret-b", "{\"a\":1}");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void signProducesADifferentSignatureForADifferentPayload() {
        String first = WebhookSignature.sign("secret", "{\"a\":1}");
        String second = WebhookSignature.sign("secret", "{\"a\":2}");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void signRejectsANullSecret() {
        assertThatThrownBy(() -> WebhookSignature.sign(null, "{}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signRejectsANullPayload() {
        assertThatThrownBy(() -> WebhookSignature.sign("secret", null)).isInstanceOf(IllegalArgumentException.class);
    }
}
