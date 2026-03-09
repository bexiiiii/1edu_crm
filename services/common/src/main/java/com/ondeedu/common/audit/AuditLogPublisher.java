package com.ondeedu.common.audit;

import com.ondeedu.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget audit log publisher.
 * Inject this bean in any service to publish audit events asynchronously.
 *
 * Usage:
 *   auditLogPublisher.publishSystem(SystemAuditEvent.builder()...build());
 *   auditLogPublisher.publishTenant(TenantAuditEvent.builder()...build());
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RabbitTemplate.class)
public class AuditLogPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishSystem(SystemAuditEvent event) {
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
