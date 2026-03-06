package com.ondeedu.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Published to audit.tenant routing key.
 * Represents actions within a specific tenant (УЦ).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAuditEvent {

    private String tenantId;     // which UZ the action belongs to
    private AuditAction action;
    private String category;     // "STUDENTS", "FINANCE", "LESSONS", etc.

    private String actorId;      // user UUID who performed the action
    private String actorName;    // display name

    private String targetType;   // "STUDENT", "GROUP", "LESSON", etc.
    private String targetId;
    private String targetName;   // human-readable

    private Map<String, Object> details; // extra context

    @Builder.Default
    private Instant timestamp = Instant.now();
}
