package com.personalfinance.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import com.personalfinance.user.dto.WebhookCreateRequestDto;
import com.personalfinance.user.dto.WebhookCreatedDto;
import com.personalfinance.user.dto.WebhookDto;
import com.personalfinance.user.entity.WebhookSubscriptionEntity;
import com.personalfinance.user.repository.WebhookSubscriptionRepository;

class WebhookSubscriptionServiceTest {

    private final UUID userId = UUID.randomUUID();

    private WebhookSubscriptionRepository subscriptions;
    private WebhookSubscriptionService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(WebhookSubscriptionRepository.class);
        service = new WebhookSubscriptionService(subscriptions);
        when(subscriptions.save(any(WebhookSubscriptionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createReturnsTheSecretExactlyOnceInTheCreatedDto() {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto("https://example.com/hook", "expense.*");

        WebhookCreatedDto created = service.create(userId, request);

        assertThat(created.secret()).isNotBlank();
        assertThat(created.url()).isEqualTo("https://example.com/hook");
        assertThat(created.eventPattern()).isEqualTo("expense.*");
    }

    @Test
    void createRejectsAnInvalidEventPattern() {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto("https://example.com/hook", "expense.$$");

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/hook", "file:///etc/passwd", "javascript:alert(1)"})
    void createRejectsNonHttpSchemes(String url) {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto(url, "expense.*");

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void createRejectsAUrlWithNoHost() {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto("https:///path", "expense.*");

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    static Stream<String> privateHosts() {
        return Stream.of(
                "http://localhost/hook",
                "http://sub.localhost/hook",
                "http://127.0.0.1/hook",
                "http://10.0.0.5/hook",
                "http://192.168.1.1/hook",
                "http://169.254.169.254/hook",
                "http://172.16.0.1/hook",
                "http://172.31.255.255/hook");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("privateHosts")
    void createRejectsPrivateOrLoopbackHosts(String url) {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto(url, "expense.*");

        assertThatThrownBy(() -> service.create(userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void createAcceptsANormalPublicHttpsUrl() {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto("https://hooks.example.com/callback", "expense.*");

        WebhookCreatedDto created = service.create(userId, request);

        assertThat(created.url()).isEqualTo("https://hooks.example.com/callback");
    }

    @Test
    void createRejectsANullUserId() {
        WebhookCreateRequestDto request = new WebhookCreateRequestDto("https://example.com/hook", "expense.*");

        assertThatThrownBy(() -> service.create(null, request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsANullRequestBody() {
        assertThatThrownBy(() -> service.create(userId, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void listScopesToTheCallersUserId() {
        WebhookSubscriptionEntity entity =
                new WebhookSubscriptionEntity(userId, "https://example.com/hook", "expense.*", "secret");
        when(subscriptions.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        List<WebhookDto> result = service.list(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(entity.getId());
    }

    @Test
    void listRejectsANullUserId() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteOnAnotherUsersSubscriptionIsNotFound() {
        UUID subscriptionId = UUID.randomUUID();
        when(subscriptions.findByIdAndUserId(subscriptionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(userId, subscriptionId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void deleteRejectsANullId() {
        assertThatThrownBy(() -> service.delete(userId, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteRejectsANullUserId() {
        assertThatThrownBy(() -> service.delete(null, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
    }
}
