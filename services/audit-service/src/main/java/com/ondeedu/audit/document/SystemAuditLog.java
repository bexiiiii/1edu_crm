package com.ondeedu.audit.document;

import com.ondeedu.common.audit.AuditAction;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * SUPER_ADMIN action log. TTL: 365 days.
 * Collection: system_audit_logs
 */
@Data
@Builder
@Document(collection = "system_audit_logs")
public class SystemAuditLog {

    @Id
    private String id;

    private AuditAction action;

    private String actorId;
    private String actorName;

    private String targetType;
    private String targetId;
    private String targetName;

    private Map<String, Object> details;

    @CreatedDate
    @Indexed(expireAfterSeconds = 31_536_000) // 365 days TTL
    private Instant timestamp;
}
