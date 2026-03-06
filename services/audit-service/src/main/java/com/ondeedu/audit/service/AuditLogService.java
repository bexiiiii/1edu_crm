package com.ondeedu.audit.service;

import com.ondeedu.audit.document.SystemAuditLog;
import com.ondeedu.audit.document.TenantAuditLog;
import com.ondeedu.audit.repository.SystemAuditLogRepository;
import com.ondeedu.audit.repository.TenantAuditLogRepository;
import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final SystemAuditLogRepository systemRepo;
    private final TenantAuditLogRepository tenantRepo;

    // ── System logs (SUPER_ADMIN) ──────────────────────────────────────────

    public PageResponse<SystemAuditLog> getSystemLogs(
            AuditAction action,
            String targetId,
            String actorId,
            Instant from,
            Instant to,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<SystemAuditLog> result;

        if (from != null && to != null) {
            result = systemRepo.findByTimestampBetweenOrderByTimestampDesc(from, to, pageable);
        } else if (action != null) {
            result = systemRepo.findByActionOrderByTimestampDesc(action, pageable);
        } else if (targetId != null) {
            result = systemRepo.findByTargetIdOrderByTimestampDesc(targetId, pageable);
        } else if (actorId != null) {
            result = systemRepo.findByActorIdOrderByTimestampDesc(actorId, pageable);
        } else {
            result = systemRepo.findByOrderByTimestampDesc(pageable);
        }

        return PageResponse.from(result);
    }

    // ── Tenant logs (per-UZ) ───────────────────────────────────────────────

    public PageResponse<TenantAuditLog> getTenantLogs(
            String tenantId,
            String category,
            AuditAction action,
            String actorId,
            Instant from,
            Instant to,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TenantAuditLog> result;

        if (from != null && to != null) {
            result = tenantRepo.findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
                    tenantId, from, to, pageable);
        } else if (category != null) {
            result = tenantRepo.findByTenantIdAndCategoryOrderByTimestampDesc(
                    tenantId, category, pageable);
        } else if (action != null) {
            result = tenantRepo.findByTenantIdAndActionOrderByTimestampDesc(
                    tenantId, action, pageable);
        } else if (actorId != null) {
            result = tenantRepo.findByTenantIdAndActorIdOrderByTimestampDesc(
                    tenantId, actorId, pageable);
        } else {
            result = tenantRepo.findByTenantIdOrderByTimestampDesc(tenantId, pageable);
        }

        return PageResponse.from(result);
    }
}
