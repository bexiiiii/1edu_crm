package com.ondeedu.common.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_CLAIM = "tenant_id";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        String tenantId = extractTenantId(request);

        if (tenantId != null) {
            TenantContext.setTenantId(tenantId);
            TenantContext.setSchemaName("tenant_" + tenantId);
            log.debug("Set tenant context: {}", tenantId);
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
            return jwt.getSubject();
        }
        return null;
    }
}
