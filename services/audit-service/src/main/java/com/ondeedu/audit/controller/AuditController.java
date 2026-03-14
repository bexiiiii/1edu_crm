package com.ondeedu.audit.controller;

import com.ondeedu.audit.document.SystemAuditLog;
import com.ondeedu.audit.document.TenantAuditLog;
import com.ondeedu.audit.service.AuditLogService;
import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "System and per-tenant activity logs")
public class AuditController {

    private final AuditLogService auditLogService;

    // ── System logs — SUPER_ADMIN only ────────────────────────────────────

    @GetMapping("/system")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "SUPER_ADMIN action log (tenant bans, plan changes, etc.)")
    public ApiResponse<PageResponse<SystemAuditLog>> getSystemLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return ApiResponse.success(
                auditLogService.getSystemLogs(action, targetId, actorId, from, to, page, size));
    }

    // ── Tenant logs — TENANT_ADMIN or SUPER_ADMIN ─────────────────────────

    @GetMapping("/tenant")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Per-UZ activity log (students, payments, lessons, etc.)")
    public ApiResponse<PageResponse<TenantAuditLog>> getTenantLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        String tenantId = resolveTenantId(authentication, jwt, tenantHeader);
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException(
                    "TENANT_CONTEXT_MISSING",
                    "Tenant context is not set. Sign in again or provide X-Tenant-ID header.",
                    org.springframework.http.HttpStatus.BAD_REQUEST
            );
        }

        return ApiResponse.success(
                auditLogService.getTenantLogs(tenantId, category, action, actorId, from, to, page, size));
    }

    private String resolveTenantId(Authentication authentication, Jwt jwt, String tenantHeader) {
        if (isSuperAdmin(authentication) && StringUtils.hasText(tenantHeader)) {
            return tenantHeader;
        }

        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }

        if (jwt != null) {
            tenantId = jwt.getClaimAsString("tenant_id");
            if (StringUtils.hasText(tenantId)) {
                return tenantId;
            }
        }

        return null;
    }

    private boolean isSuperAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }
}
