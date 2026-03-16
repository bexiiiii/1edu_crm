package com.ondeedu.task.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.AssignmentNotificationEvent;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.task.client.StaffGrpcClient;
import com.ondeedu.task.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskAssignmentNotificationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final StaffGrpcClient staffGrpcClient;
    private final RabbitTemplate rabbitTemplate;

    public void notifyIfAssigned(UUID previousAssignedTo, Task task) {
        if (task.getAssignedTo() == null || task.getAssignedTo().equals(previousAssignedTo)) {
            return;
        }

        staffGrpcClient.findRecipient(task.getAssignedTo()).ifPresentOrElse(
                recipient -> publishAssignment(task, recipient),
                () -> log.warn("Skipping task assignment notification, assignee {} could not be resolved", task.getAssignedTo())
        );
    }

    private void publishAssignment(Task task, StaffGrpcClient.StaffRecipient recipient) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ASSIGNMENT_KEY,
                    new AssignmentNotificationEvent(
                            "task.assigned",
                            TenantContext.getTenantId(),
                            TenantContext.getUserId(),
                            recipient.staffId(),
                            recipient.email(),
                            recipient.fullName(),
                            "TASK",
                            task.getId(),
                            task.getTitle(),
                            "Вам назначена задача",
                            buildBody(task)
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to publish task assignment notification for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private String buildBody(Task task) {
        StringBuilder body = new StringBuilder("Вам назначена задача \"")
                .append(task.getTitle())
                .append("\".");

        if (task.getDueDate() != null) {
            body.append(" Срок: ").append(task.getDueDate().format(DATE_FORMATTER)).append(".");
        }

        return body.toString();
    }
}
