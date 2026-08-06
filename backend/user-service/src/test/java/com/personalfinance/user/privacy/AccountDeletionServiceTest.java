package com.personalfinance.user.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.personalfinance.user.entity.ErasureRequestEntity;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.events.UserErasureRequestedEvent;
import com.personalfinance.user.repository.ErasureRequestRepository;
import com.personalfinance.user.repository.UserRepository;
import com.personalfinance.user.service.AccountDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

class AccountDeletionServiceTest {

    private UserRepository users;
    private ErasureRequestRepository erasureRequests;
    private ApplicationEventPublisher eventPublisher;
    private AccountDeletionService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        erasureRequests = mock(ErasureRequestRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new AccountDeletionService(users, erasureRequests, eventPublisher,
                List.of("user", "expense"));
        when(erasureRequests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void initiateErasureDeletesTheUserAndOpensAnAuditTrail() {
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        UUID requestId = service.initiateErasure(user.getId());

        verify(users).delete(user);

        ArgumentCaptor<ErasureRequestEntity> saved = ArgumentCaptor.forClass(ErasureRequestEntity.class);
        verify(erasureRequests).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(requestId);
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(saved.getValue().expectedServiceNames()).containsExactlyInAnyOrder("user", "expense");
        assertThat(saved.getValue().completedServiceNames()).containsExactly("user");
        assertThat(saved.getValue().getStatus()).isEqualTo(ErasureRequestEntity.STATUS_PENDING);
    }

    @Test
    void initiateErasurePublishesTheFanoutForTheSameRequestId() {
        UserEntity user = new UserEntity("alex@example.com", null, "Alex", null);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        UUID requestId = service.initiateErasure(user.getId());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOf(UserErasureRequestedEvent.class);

        UserErasureRequestedEvent event = (UserErasureRequestedEvent) published.getValue();
        assertThat(event.erasureRequestId()).isEqualTo(requestId);
        assertThat(event.userId()).isEqualTo(user.getId());
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void initiateErasureRejectsAnUnknownUserWithoutPublishingAnything() {
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateErasure(unknown))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Account not found");

        verify(erasureRequests, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void initiateErasureRejectsAMissingPrincipal() {
        assertThatThrownBy(() -> service.initiateErasure(null))
                .isInstanceOf(ResponseStatusException.class);

        verify(users, never()).delete(any());
    }

    @Test
    void requestCompletesOnlyOnceEveryExpectedServiceHasAcked() {
        ErasureRequestEntity request = pendingRequest();
        when(erasureRequests.findById(request.getId())).thenReturn(Optional.of(request));

        assertThat(request.getStatus()).isEqualTo(ErasureRequestEntity.STATUS_PENDING);

        service.recordServiceAck(request.getId(), "expense");

        assertThat(request.isComplete()).isTrue();
        assertThat(request.getStatus()).isEqualTo(ErasureRequestEntity.STATUS_COMPLETED);
        assertThat(request.getCompletedAt()).isNotNull();
    }

    @Test
    void anAckFromAServiceOutsideTheExpectedSetLeavesTheRequestPending() {
        ErasureRequestEntity request = new ErasureRequestEntity(
                UUID.randomUUID(), List.of("user", "expense", "report"), Set.of("user"));
        when(erasureRequests.findById(request.getId())).thenReturn(Optional.of(request));

        service.recordServiceAck(request.getId(), "expense");

        assertThat(request.getStatus()).isEqualTo(ErasureRequestEntity.STATUS_PENDING);
        assertThat(request.getCompletedAt()).isNull();
    }

    @Test
    void aDuplicateAckIsANoOp() {
        ErasureRequestEntity request = pendingRequest();
        when(erasureRequests.findById(request.getId())).thenReturn(Optional.of(request));

        service.recordServiceAck(request.getId(), "expense");
        Instant firstCompletedAt = request.getCompletedAt();

        service.recordServiceAck(request.getId(), "expense");

        assertThat(request.completedServiceNames()).containsExactlyInAnyOrder("user", "expense");
        assertThat(request.getCompletedAt()).isEqualTo(firstCompletedAt);
        assertThat(request.getStatus()).isEqualTo(ErasureRequestEntity.STATUS_COMPLETED);
    }

    @Test
    void anAckForAnUnknownRequestIsIgnoredRatherThanThrown() {
        UUID stray = UUID.randomUUID();
        when(erasureRequests.findById(stray)).thenReturn(Optional.empty());

        service.recordServiceAck(stray, "expense");

        verify(erasureRequests, never()).save(any());
    }

    @Test
    void anAckWithMissingFieldsIsIgnoredRatherThanThrown() {
        service.recordServiceAck(null, "expense");
        service.recordServiceAck(UUID.randomUUID(), null);
        service.recordServiceAck(UUID.randomUUID(), "   ");

        verify(erasureRequests, never()).save(any());
    }

    private static ErasureRequestEntity pendingRequest() {
        return new ErasureRequestEntity(UUID.randomUUID(), List.of("user", "expense"), Set.of("user"));
    }
}
