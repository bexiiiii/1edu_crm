package com.ondeedu.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailNotificationEvent extends BaseEvent {

    private String recipientEmail;
    private String subject;
    private String body;

    public EmailNotificationEvent(
            String eventType,
            String tenantId,
            String userId,
            String recipientEmail,
            String subject,
            String body
    ) {
        super(eventType, tenantId, userId);
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
    }
}
