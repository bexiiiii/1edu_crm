package com.ondeedu.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Published to audit.system routing key.
 * Represents SUPER_ADMIN actions (tenant management, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemAuditEvent {

    private AuditAction action;

    private String actorId;      // SUPER_ADMIN user UUID
    private String actorName;    // display name

    private String targetType;   // "TENANT", "USER", etc.
    private String targetId;
    private String targetName;   // human-readable (e.g. tenant name)

    private Map<String, Object> details; // extra context (reason, plan, etc.)

    @Builder.Default
    private Instant timestamp = Instant.now();
}
