package com.personalfinance.notification.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.personalfinance.notification.dto.NotificationDto;
import com.personalfinance.notification.dto.NotificationExportDto;
import com.personalfinance.notification.entity.NotificationEntity;

/** Entity -> DTO translation. The entity itself never leaves the service layer. */
@Component
public class NotificationMapper {

    public NotificationDto toDto(NotificationEntity entity) {
        return new NotificationDto(entity.getId(), entity.getType(), entity.getTitle(), entity.getBody(),
                entity.getCreatedAt(), entity.getReadAt() != null);
    }

    public List<NotificationDto> toDtos(List<NotificationEntity> entities) {
        return entities.stream().map(this::toDto).toList();
    }

    public NotificationExportDto toExportDto(NotificationEntity entity) {
        return new NotificationExportDto(entity.getId(), entity.getType(), entity.getTitle(), entity.getBody(),
                entity.getData(), entity.getReadAt(), entity.getCreatedAt());
    }

    public List<NotificationExportDto> toExportDtos(List<NotificationEntity> entities) {
        return entities.stream().map(this::toExportDto).toList();
    }
}
