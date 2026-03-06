package com.ondeedu.notification.mapper;

import com.ondeedu.notification.dto.NotificationDto;
import com.ondeedu.notification.entity.NotificationLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationDto toDto(NotificationLog notificationLog);
}
