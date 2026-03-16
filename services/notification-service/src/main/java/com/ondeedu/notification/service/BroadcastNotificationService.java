package com.ondeedu.notification.service;

import com.ondeedu.notification.dto.BroadcastNotificationRequest;
import com.ondeedu.notification.dto.BroadcastNotificationResultDto;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastNotificationService {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("^[a-zA-Z0-9_]+$");

    private final EntityManager entityManager;
    private final NotificationService notificationService;

    @Transactional
    public BroadcastNotificationResultDto broadcast(String tenantId, BroadcastNotificationRequest request) {
        List<TenantTarget> targets = resolveTargets(tenantId);
        int recipients = 0;

        for (TenantTarget target : targets) {
            for (Recipient recipient : findRecipients(target.schemaName())) {
                notificationService.createInAppNotification(
                        target.tenantId(),
                        recipient.email(),
                        recipient.staffId(),
                        recipient.fullName(),
                        request.getSubject(),
                        request.getBody(),
                        "system.broadcast",
                        "BROADCAST",
                        null
                );
                if (request.isAlsoEmail()) {
                    notificationService.sendEmailNotification(
                            target.tenantId(),
                            "system.broadcast.email",
                            recipient.email(),
                            request.getSubject(),
                            request.getBody()
                    );
                }
                recipients++;
            }
        }

        return BroadcastNotificationResultDto.builder()
                .scope(StringUtils.hasText(tenantId) ? tenantId : "ALL_TENANTS")
                .tenantsAffected(targets.size())
                .recipients(recipients)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<TenantTarget> resolveTargets(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                    SELECT id, schema_name
                    FROM system.tenants
                    WHERE id = CAST(:tenantId AS uuid)
                    """)
                    .setParameter("tenantId", tenantId)
                    .getSingleResult();
            return List.of(new TenantTarget(row[0].toString(), row[1].toString()));
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, schema_name
                FROM system.tenants
                WHERE schema_name IS NOT NULL
                  AND deleted_at IS NULL
                ORDER BY created_at ASC
                """).getResultList();

        List<TenantTarget> targets = new ArrayList<>();
        for (Object[] row : rows) {
            targets.add(new TenantTarget(row[0].toString(), row[1].toString()));
        }
        return targets;
    }

    @SuppressWarnings("unchecked")
    private List<Recipient> findRecipients(String schemaName) {
        if (!SAFE_SCHEMA.matcher(schemaName).matches()) {
            log.warn("Skipping broadcast for unsafe schema name {}", schemaName);
            return List.of();
        }

        String sql = """
                SELECT id, email, TRIM(CONCAT(first_name, ' ', last_name))
                FROM %s.staff
                WHERE status = 'ACTIVE'
                  AND email IS NOT NULL
                  AND email <> ''
                ORDER BY first_name, last_name
                """.formatted(schemaName);

        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        List<Recipient> recipients = new ArrayList<>();
        for (Object[] row : rows) {
            recipients.add(new Recipient((UUID) row[0], (String) row[1], (String) row[2]));
        }
        return recipients;
    }

    private record TenantTarget(String tenantId, String schemaName) {
    }

    private record Recipient(UUID staffId, String email, String fullName) {
    }
}
