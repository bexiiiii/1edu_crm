package com.ondeedu.audit.listener;

import com.ondeedu.audit.document.SystemAuditLog;
import com.ondeedu.audit.document.TenantAuditLog;
import com.ondeedu.audit.repository.SystemAuditLogRepository;
import com.ondeedu.audit.repository.TenantAuditLogRepository;
import com.ondeedu.common.audit.SystemAuditEvent;
import com.ondeedu.common.audit.TenantAuditEvent;
import com.ondeedu.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogListener {

    private final SystemAuditLogRepository systemRepo;
    private final TenantAuditLogRepository tenantRepo;

    @RabbitListener(queues = RabbitMQConfig.AUDIT_SYSTEM_QUEUE)
    public void onSystemAuditEvent(SystemAuditEvent event) {
        try {
            SystemAuditLog doc = SystemAuditLog.builder()
                    .action(event.getAction())
                    .actorId(event.getActorId())
                    .actorName(event.getActorName())
                    .targetType(event.getTargetType())
                    .targetId(event.getTargetId())
                    .targetName(event.getTargetName())
                    .details(event.getDetails())
                    .timestamp(event.getTimestamp())
                    .build();
            systemRepo.save(doc);
            log.debug("Saved system audit log: {} on {}", event.getAction(), event.getTargetId());
        } catch (Exception e) {
            log.error("Failed to save system audit log {}: {}", event.getAction(), e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.AUDIT_TENANT_QUEUE)
    public void onTenantAuditEvent(TenantAuditEvent event) {
        try {
            TenantAuditLog doc = TenantAuditLog.builder()
                    .tenantId(event.getTenantId())
                    .action(event.getAction())
                    .category(event.getCategory())
                    .actorId(event.getActorId())
                    .actorName(event.getActorName())
                    .targetType(event.getTargetType())
                    .targetId(event.getTargetId())
                    .targetName(event.getTargetName())
                    .details(event.getDetails())
                    .timestamp(event.getTimestamp())
                    .build();
            tenantRepo.save(doc);
            log.debug("Saved tenant audit log: tenant={} action={}", event.getTenantId(), event.getAction());
        } catch (Exception e) {
            log.error("Failed to save tenant audit log {}: {}", event.getAction(), e.getMessage());
        }
    }
}
