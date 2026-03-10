package com.ondeedu.common.audit;

import com.ondeedu.common.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget audit log publisher.
 * Inject this bean in any service to publish audit events asynchronously.
 * RabbitTemplate is optional — services without RabbitMQ get a no-op publisher.
 *
 * Usage:
 *   auditLogPublisher.publishSystem(SystemAuditEvent.builder()...build());
 *   auditLogPublisher.publishTenant(TenantAuditEvent.builder()...build());
 */
@Slf4j
@Component
public class AuditLogPublisher {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    public void publishSystem(SystemAuditEvent event) {
        if (rabbitTemplate == null) return;
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AUDIT_EXCHANGE,
                    RabbitMQConfig.AUDIT_SYSTEM_KEY,
                    event);
        } catch (Exception e) {
            log.error("Failed to publish system audit event {}: {}", event.getAction(), e.getMessage());
        }
    }

    public void publishTenant(TenantAuditEvent event) {
        if (rabbitTemplate == null) return;
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AUDIT_EXCHANGE,
                    RabbitMQConfig.AUDIT_TENANT_KEY,
                    event);
        } catch (Exception e) {
            log.error("Failed to publish tenant audit event {}: {}", event.getAction(), e.getMessage());
        }
    }
}
