package com.ondeedu.lead.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.AssignmentNotificationEvent;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.lead.client.StaffGrpcClient;
import com.ondeedu.lead.entity.Lead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadAssignmentNotificationService {

    private final StaffGrpcClient staffGrpcClient;
    private final RabbitTemplate rabbitTemplate;

    public void notifyIfAssigned(String previousAssignedTo, Lead lead) {
        if (!StringUtils.hasText(lead.getAssignedTo()) || lead.getAssignedTo().equals(previousAssignedTo)) {
            return;
        }

        UUID assigneeId;
        try {
            assigneeId = UUID.fromString(lead.getAssignedTo());
        } catch (IllegalArgumentException e) {
            log.warn("Skipping lead assignment notification, assignee '{}' is not a valid UUID", lead.getAssignedTo());
            return;
        }

        staffGrpcClient.findRecipient(assigneeId).ifPresentOrElse(
                recipient -> publishAssignment(lead, recipient),
                () -> log.warn("Skipping lead assignment notification, assignee {} could not be resolved", assigneeId)
        );
    }

    private void publishAssignment(Lead lead, StaffGrpcClient.StaffRecipient recipient) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ASSIGNMENT_KEY,
                    new AssignmentNotificationEvent(
                            "lead.assigned",
                            TenantContext.getTenantId(),
                            TenantContext.getUserId(),
                            recipient.staffId(),
                            recipient.email(),
                            recipient.fullName(),
                            "LEAD",
                            lead.getId(),
                            lead.getFullName(),
                            "Вам назначен лид",
                            "Вам назначен лид \"" + lead.getFullName() + "\"."
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to publish lead assignment notification for lead {}: {}", lead.getId(), e.getMessage());
        }
    }
}
