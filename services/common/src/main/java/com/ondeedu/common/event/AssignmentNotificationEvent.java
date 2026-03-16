package com.ondeedu.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssignmentNotificationEvent extends BaseEvent {

    private UUID recipientStaffId;
    private String recipientEmail;
    private String recipientName;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private String subject;
    private String body;

    public AssignmentNotificationEvent(
            String eventType,
            String tenantId,
            String userId,
            UUID recipientStaffId,
            String recipientEmail,
            String recipientName,
            String entityType,
            UUID entityId,
            String entityName,
            String subject,
            String body
    ) {
        super(eventType, tenantId, userId);
        this.recipientStaffId = recipientStaffId;
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
        this.subject = subject;
        this.body = body;
    }
}
