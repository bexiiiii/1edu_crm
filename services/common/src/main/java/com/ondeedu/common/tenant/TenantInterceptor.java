package com.ondeedu.common.tenant;

import com.ondeedu.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_CLAIM = "tenant_id";

    @Value("${ondeedu.multitenancy.enabled:true}")
    private boolean multitenancyEnabled;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        String tenantId = extractTenantId(request);

        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
        }

        String schemaName = TenantSchemaResolver.schemaNameForTenantId(tenantId);
        if (multitenancyEnabled) {
            if (!StringUtils.hasText(tenantId)) {
                throw new BusinessException(
                        "TENANT_CONTEXT_REQUIRED",
                        "Tenant context is required for this operation",
                        HttpStatus.BAD_REQUEST
                );
            }
            if (!StringUtils.hasText(schemaName)) {
                throw new BusinessException(
                        "INVALID_TENANT_ID",
                        "Invalid tenant identifier",
                        HttpStatus.BAD_REQUEST
                );
            }

            TenantContext.setSchemaName(schemaName);
            log.debug("Set tenant context: {} -> {}", tenantId, schemaName);
        } else if (StringUtils.hasText(schemaName)) {
            TenantContext.setSchemaName(schemaName);
        } else if (StringUtils.hasText(tenantId)) {
            log.warn("Ignoring invalid tenant id for schema resolution: {}", tenantId);
        }

        String userId = extractUserId();
        if (userId != null) {
            TenantContext.setUserId(userId);
        }

        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        TenantContext.clear();
    }

    private String extractTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Only SUPER_ADMIN can override tenant via X-Tenant-ID header
        boolean isSuperAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

        if (isSuperAdmin) {
            String tenantFromHeader = request.getHeader(TENANT_HEADER);
            if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
                log.debug("SUPER_ADMIN override tenant via header: {}", tenantFromHeader);
                return tenantFromHeader;
            }
        }

        // All users: resolve tenant from JWT claim
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString(TENANT_CLAIM);
        }

        return null;
    }

    private String extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            if (StringUtils.hasText(jwt.getSubject())) {
                return jwt.getSubject();
            }
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (StringUtils.hasText(preferredUsername)) {
                return preferredUsername;
            }
            String email = jwt.getClaimAsString("email");
            if (StringUtils.hasText(email)) {
                return email;
            }
        }
        return null;
    }
}
