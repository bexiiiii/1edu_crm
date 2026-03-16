package com.ondeedu.notification.dto;

import com.ondeedu.notification.entity.NotificationStatus;
import com.ondeedu.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private UUID id;
    private NotificationType type;
    private String recipientEmail;
    private UUID recipientStaffId;
    private String recipientName;
    private String recipientPhone;
    private String subject;
    private String body;
    private NotificationStatus status;
    private String errorMessage;
    private Instant sentAt;
    private String tenantId;
    private String eventType;
    private String referenceType;
    private UUID referenceId;
    private Instant createdAt;
}
