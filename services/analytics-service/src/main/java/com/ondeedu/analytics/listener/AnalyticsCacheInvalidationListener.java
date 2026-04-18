package com.ondeedu.analytics.listener;

import com.ondeedu.analytics.config.AnalyticsCacheInvalidationRabbitConfig;
import com.ondeedu.analytics.service.AnalyticsCacheInvalidationService;
import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.TenantAuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsCacheInvalidationListener {

    private static final Set<AuditAction> ANALYTICS_RELEVANT_ACTIONS = EnumSet.of(
            AuditAction.STUDENT_CREATED,
            AuditAction.STUDENT_UPDATED,
            AuditAction.STUDENT_DELETED,
            AuditAction.STUDENT_STATUS_CHANGED,
            AuditAction.STAFF_CREATED,
            AuditAction.STAFF_UPDATED,
            AuditAction.STAFF_DELETED,
            AuditAction.GROUP_CREATED,
            AuditAction.GROUP_UPDATED,
            AuditAction.GROUP_DELETED,
            AuditAction.STUDENT_ENROLLED,
            AuditAction.STUDENT_REMOVED_FROM_GROUP,
            AuditAction.LESSON_CREATED,
            AuditAction.LESSON_UPDATED,
            AuditAction.LESSON_COMPLETED,
            AuditAction.LESSON_CANCELLED,
            AuditAction.LESSON_RESCHEDULED,
            AuditAction.ATTENDANCE_MARKED,
            AuditAction.PAYMENT_CREATED,
            AuditAction.PAYMENT_UPDATED,
            AuditAction.PAYMENT_DELETED,
            AuditAction.SUBSCRIPTION_CREATED,
            AuditAction.SUBSCRIPTION_UPDATED,
            AuditAction.EXPENSE_CREATED,
            AuditAction.EXPENSE_DELETED,
            AuditAction.LEAD_CREATED,
            AuditAction.LEAD_UPDATED,
            AuditAction.LEAD_CONVERTED,
            AuditAction.LEAD_DELETED
    );

    private final AnalyticsCacheInvalidationService invalidationService;

    @RabbitListener(queues = AnalyticsCacheInvalidationRabbitConfig.ANALYTICS_CACHE_INVALIDATION_QUEUE)
    public void onTenantAuditEvent(TenantAuditEvent event) {
        if (event == null || event.getAction() == null || event.getTenantId() == null || event.getTenantId().isBlank()) {
            return;
        }

        if (!ANALYTICS_RELEVANT_ACTIONS.contains(event.getAction())) {
            return;
        }

        invalidationService.invalidateTenantCaches(event.getTenantId());
        log.debug("Analytics cache invalidated for tenant {} by action {}", event.getTenantId(), event.getAction());
    }
}
