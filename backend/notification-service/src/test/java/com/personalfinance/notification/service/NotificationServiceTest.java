package com.personalfinance.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.personalfinance.notification.dto.NotificationDto;
import com.personalfinance.notification.dto.NotificationListDto;
import com.personalfinance.notification.entity.NotificationEntity;
import com.personalfinance.notification.exception.NotFoundException;
import com.personalfinance.notification.mapper.NotificationMapper;
import com.personalfinance.notification.repository.NotificationRepository;

class NotificationServiceTest {

    private final UUID userId = UUID.randomUUID();

    private NotificationRepository notifications;
    private SimpMessagingTemplate messaging;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        messaging = mock(SimpMessagingTemplate.class);
        service = new NotificationService(notifications, new NotificationMapper(), messaging);
        when(notifications.save(any(NotificationEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private NotificationEntity notification(String type, String title, String body) {
        return new NotificationEntity(userId, type, title, body, Map.of("questId", UUID.randomUUID().toString()));
    }

    // --- listFor -------------------------------------------------------------

    @Test
    void listForMapsEntitiesToDtosAndCarriesTheUnreadCount() {
        NotificationEntity unread = notification("quest.completed", "Quest completed", "Walk the flock");
        when(notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(unread));
        when(notifications.countByUserIdAndReadAtIsNull(userId)).thenReturn(1L);

        NotificationListDto result = service.listFor(userId);

        assertThat(result.unread()).isEqualTo(1L);
        assertThat(result.items()).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(unread.getId());
            assertThat(dto.type()).isEqualTo("quest.completed");
            assertThat(dto.title()).isEqualTo("Quest completed");
            assertThat(dto.body()).isEqualTo("Walk the flock");
            assertThat(dto.read()).isFalse();
        });
    }

    @Test
    void listForRejectsANullUserIdInsteadOfQueryingForNull() {
        assertThatThrownBy(() -> service.listFor(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");

        verifyNoInteractions(messaging);
        verify(notifications, never()).findTop50ByUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void listForReturnsAnEmptyListAndZeroUnreadForAUserWithNoNotifications() {
        when(notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(notifications.countByUserIdAndReadAtIsNull(userId)).thenReturn(0L);

        NotificationListDto result = service.listFor(userId);

        assertThat(result.items()).isEmpty();
        assertThat(result.unread()).isZero();
    }

    @Test
    void listForPropagatesARepositoryFailure() {
        when(notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId))
                .thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.listFor(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    // --- markRead ------------------------------------------------------------

    @Test
    void markReadStampsTheReadTimestampOnTheUsersOwnNotification() {
        NotificationEntity entity = notification("quest.failed", "Quest failed", "Missed the window");
        when(notifications.findByIdAndUserId(entity.getId(), userId)).thenReturn(Optional.of(entity));

        service.markRead(entity.getId(), userId);

        assertThat(entity.getReadAt()).isNotNull();
    }

    @Test
    void markReadRejectsANullIdAsNotFoundRatherThanQueryingForNull() {
        assertThatThrownBy(() -> service.markRead(null, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notification not found");

        verify(notifications, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void markReadRejectsANullUserId() {
        assertThatThrownBy(() -> service.markRead(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");
    }

    @Test
    void markReadThrowsNotFoundWhenTheNotificationBelongsToSomeoneElse() {
        UUID otherUsersNotification = UUID.randomUUID();
        when(notifications.findByIdAndUserId(otherUsersNotification, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(otherUsersNotification, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notification not found");
    }

    @Test
    void markReadIsIdempotentAndKeepsTheFirstReadTimestamp() {
        NotificationEntity entity = notification("quest.suggested", "New quest suggested", "Tally the herd");
        when(notifications.findByIdAndUserId(entity.getId(), userId)).thenReturn(Optional.of(entity));

        service.markRead(entity.getId(), userId);
        Instant firstReadAt = entity.getReadAt();
        service.markRead(entity.getId(), userId);

        assertThat(entity.getReadAt()).isEqualTo(firstReadAt);
    }

    // --- markAllRead ---------------------------------------------------------

    @Test
    void markAllReadStampsEveryUnreadNotification() {
        NotificationEntity first = notification("quest.completed", "Quest completed", "One");
        NotificationEntity second = notification("quest.failed", "Quest failed", "Two");
        when(notifications.findByUserIdAndReadAtIsNull(userId)).thenReturn(List.of(first, second));

        service.markAllRead(userId);

        assertThat(first.getReadAt()).isNotNull();
        assertThat(second.getReadAt()).isNotNull();
    }

    @Test
    void markAllReadRejectsANullUserId() {
        assertThatThrownBy(() -> service.markAllRead(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");

        verify(notifications, never()).findByUserIdAndReadAtIsNull(any());
    }

    @Test
    void markAllReadIsANoOpWhenNothingIsUnread() {
        when(notifications.findByUserIdAndReadAtIsNull(userId)).thenReturn(List.of());

        service.markAllRead(userId);

        verify(notifications).findByUserIdAndReadAtIsNull(userId);
    }

    @Test
    void markAllReadPropagatesARepositoryFailure() {
        when(notifications.findByUserIdAndReadAtIsNull(userId)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.markAllRead(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }

    // --- recordQuestNotification --------------------------------------------

    @Test
    void recordQuestNotificationPersistsTheEventAndPushesItToTheUsersSocket() {
        UUID questId = UUID.randomUUID();

        NotificationDto dto = service.recordQuestNotification(userId, "quest.completed", "Walk the flock", questId);

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("quest.completed");
        assertThat(saved.getValue().getTitle()).isEqualTo("Quest completed 🎉");
        assertThat(saved.getValue().getBody()).isEqualTo("Walk the flock");
        assertThat(saved.getValue().getData()).containsEntry("questId", questId.toString());

        assertThat(dto.title()).isEqualTo("Quest completed 🎉");
        verify(messaging).convertAndSendToUser(eq(userId.toString()), eq("/queue/notifications"), eq(dto));
    }

    @Test
    void recordQuestNotificationRejectsANullUserIdWithoutPersistingAnything() {
        assertThatThrownBy(() -> service.recordQuestNotification(null, "quest.completed", "Walk the flock",
                UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");

        verify(notifications, never()).save(any());
        verifyNoInteractions(messaging);
    }

    @Test
    void recordQuestNotificationSubstitutesADefaultBodyWhenTheQuestTitleIsNull() {
        service.recordQuestNotification(userId, "quest.failed", null, UUID.randomUUID());

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getBody()).isEqualTo("A quest was updated.");
    }

    @Test
    void recordQuestNotificationSubstitutesADefaultBodyWhenTheQuestTitleIsBlank() {
        service.recordQuestNotification(userId, "quest.failed", "   ", UUID.randomUUID());

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getBody()).isEqualTo("A quest was updated.");
    }

    @Test
    void recordQuestNotificationOmitsTheQuestIdFromThePayloadWhenItIsNull() {
        service.recordQuestNotification(userId, "quest.suggested", "Tally the herd", null);

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getData()).isEmpty();
    }

    @Test
    void recordQuestNotificationFallsBackToAGenericTypeAndTitleWhenTheRoutingKeyIsNull() {
        service.recordQuestNotification(userId, null, "Tally the herd", UUID.randomUUID());

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("quest.updated");
        assertThat(saved.getValue().getTitle()).isEqualTo("Quest update");
    }

    @Test
    void recordQuestNotificationFallsBackToAGenericTitleForAnUnrecognisedRoutingKey() {
        service.recordQuestNotification(userId, "quest.reopened", "Tally the herd", UUID.randomUUID());

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("quest.reopened");
        assertThat(saved.getValue().getTitle()).isEqualTo("Quest update");
    }

    @Test
    void recordQuestNotificationDoesNotPushWhenPersistenceFails() {
        when(notifications.save(any(NotificationEntity.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.recordQuestNotification(userId, "quest.completed", "Walk the flock",
                UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        verifyNoInteractions(messaging);
    }
}
