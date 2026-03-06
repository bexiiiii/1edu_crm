package com.ondeedu.audit.document;

import com.ondeedu.common.audit.AuditAction;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Per-tenant activity log. TTL: 90 days.
 * Collection: tenant_audit_logs
 * Indexed by tenantId + timestamp for fast per-UZ queries.
 */
@Data
@Builder
@Document(collection = "tenant_audit_logs")
@CompoundIndex(name = "tenant_ts_idx", def = "{'tenantId': 1, 'timestamp': -1}")
public class TenantAuditLog {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private AuditAction action;
    private String category;   // "STUDENTS", "FINANCE", "LESSONS", etc.

    private String actorId;
    private String actorName;

    private String targetType;
    private String targetId;
    private String targetName;

    private Map<String, Object> details;

    @CreatedDate
    @Indexed(expireAfterSeconds = 7_776_000) // 90 days TTL
    private Instant timestamp;
}
