package com.personalfinance.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.personalfinance.notification.dto.NotificationDto;
import com.personalfinance.notification.dto.NotificationExportDto;
import com.personalfinance.notification.entity.NotificationEntity;

class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapper();
    private final UUID userId = UUID.randomUUID();

    @Test
    void toDtoReportsAnUnstampedNotificationAsUnread() {
        NotificationEntity entity = new NotificationEntity(userId, "quest.failed", "Quest failed", "Missed it",
                Map.of());

        NotificationDto dto = mapper.toDto(entity);

        assertThat(dto.read()).isFalse();
        assertThat(dto.id()).isEqualTo(entity.getId());
        assertThat(dto.createdAt()).isEqualTo(entity.getCreatedAt());
    }

    @Test
    void toDtoReportsAStampedNotificationAsRead() {
        NotificationEntity entity = new NotificationEntity(userId, "quest.failed", "Quest failed", "Missed it",
                Map.of());
        entity.markRead();

        assertThat(mapper.toDto(entity).read()).isTrue();
    }

    @Test
    void toDtoNeverExposesTheUserIdOrThePayload() {
        NotificationEntity entity = new NotificationEntity(userId, "quest.completed", "Quest completed", "Done",
                Map.of("questId", "q-1"));

        assertThat(NotificationDto.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "type", "title", "body", "createdAt", "read");
        assertThat(mapper.toDto(entity)).isNotNull();
    }

    @Test
    void toDtosMapsAnEmptyListToAnEmptyList() {
        assertThat(mapper.toDtos(List.of())).isEmpty();
    }

    @Test
    void toExportDtoNormalisesANullPayloadToAnEmptyMap() {
        NotificationEntity entity = new NotificationEntity(userId, "quest.suggested", "New quest", "Body", null);

        NotificationExportDto dto = mapper.toExportDto(entity);

        assertThat(dto.data()).isEmpty();
        assertThat(dto.readAt()).isNull();
    }

    @Test
    void toExportDtosMapsAnEmptyListToAnEmptyList() {
        assertThat(mapper.toExportDtos(List.of())).isEmpty();
    }
}
