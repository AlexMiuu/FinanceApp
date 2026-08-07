package com.personalfinance.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.personalfinance.notification.dto.NotificationDataExportDto;
import com.personalfinance.notification.entity.NotificationEntity;
import com.personalfinance.notification.events.Events;
import com.personalfinance.notification.mapper.NotificationMapper;
import com.personalfinance.notification.repository.NotificationRepository;

class PrivacyServiceTest {

    private final UUID userId = UUID.randomUUID();

    private NotificationRepository notifications;
    private List<Object> published;
    private PrivacyService service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        published = new ArrayList<>();
        service = new PrivacyService(notifications, new NotificationMapper(), published::add);
    }

    // --- eraseUserData -------------------------------------------------------

    @Test
    void eraseUserDataDeletesTheUsersNotificationsAndPublishesCompletion() {
        UUID erasureRequestId = UUID.randomUUID();

        service.eraseUserData(erasureRequestId, userId);

        verify(notifications).deleteByUserId(userId);
        assertThat(published).singleElement().isInstanceOfSatisfying(Events.ErasureCompleted.class, event -> {
            assertThat(event.erasureRequestId()).isEqualTo(erasureRequestId);
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.service()).isEqualTo("notification");
            assertThat(event.routingKey()).isEqualTo("user.erasure.completed");
        });
    }

    @Test
    void eraseUserDataRejectsANullUserIdWithoutDeletingOrPublishing() {
        assertThatThrownBy(() -> service.eraseUserData(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");

        verify(notifications, never()).deleteByUserId(any());
        assertThat(published).isEmpty();
    }

    @Test
    void eraseUserDataStillAcknowledgesWhenTheUserHadNoNotifications() {
        when(notifications.deleteByUserId(userId)).thenReturn(0L);

        service.eraseUserData(UUID.randomUUID(), userId);

        assertThat(published).hasSize(1);
    }

    @Test
    void eraseUserDataPublishesNothingWhenTheDeleteFails() {
        when(notifications.deleteByUserId(userId)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.eraseUserData(UUID.randomUUID(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        assertThat(published).isEmpty();
    }

    // --- exportUserData ------------------------------------------------------

    @Test
    void exportUserDataCarriesEveryPersonalColumnIncludingPayloadAndReadState() {
        NotificationEntity entity = new NotificationEntity(userId, "quest.completed", "Quest completed 🎉",
                "Walk the flock", Map.of("questId", "q-1"));
        entity.markRead();
        when(notifications.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        NotificationDataExportDto export = service.exportUserData(userId);

        assertThat(export.notifications()).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(entity.getId());
            assertThat(dto.type()).isEqualTo("quest.completed");
            assertThat(dto.title()).isEqualTo("Quest completed 🎉");
            assertThat(dto.body()).isEqualTo("Walk the flock");
            assertThat(dto.data()).containsEntry("questId", "q-1");
            assertThat(dto.readAt()).isEqualTo(entity.getReadAt());
            assertThat(dto.createdAt()).isEqualTo(entity.getCreatedAt());
        });
    }

    @Test
    void exportUserDataRejectsANullUserId() {
        assertThatThrownBy(() -> service.exportUserData(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required");

        verify(notifications, never()).findByUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void exportUserDataReturnsAnEmptyListNotNullForAUserWithNoData() {
        when(notifications.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        NotificationDataExportDto export = service.exportUserData(userId);

        assertThat(export.notifications()).isEmpty();
    }

    @Test
    void exportUserDataPropagatesARepositoryFailure() {
        when(notifications.findByUserIdOrderByCreatedAtDesc(userId)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.exportUserData(userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}
