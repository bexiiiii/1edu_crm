package com.ondeedu.common.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_CLAIM = "tenant_id";

    private final ObjectMapper objectMapper;

    @Value("${ondeedu.multitenancy.enabled:true}")
    private boolean multitenancyEnabled;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path) || !path.startsWith("/api/")) {
            return true;
        }

        return "/api/v1/settings/google-drive-backup/oauth/callback".equals(path)
                || "/api/v1/settings/yandex-disk-backup/oauth/callback".equals(path);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String tenantId = extractTenantId(request);
            if (StringUtils.hasText(tenantId)) {
                TenantContext.setTenantId(tenantId);
            }

            String schemaName = TenantSchemaResolver.schemaNameForTenantId(tenantId);
            if (multitenancyEnabled) {
                if (!StringUtils.hasText(tenantId)) {
                    writeBusinessError(response,
                            HttpServletResponse.SC_BAD_REQUEST,
                            "TENANT_CONTEXT_REQUIRED",
                            "Tenant context is required for this operation");
                    return;
                }
                if (!StringUtils.hasText(schemaName)) {
                    writeBusinessError(response,
                            HttpServletResponse.SC_BAD_REQUEST,
                            "INVALID_TENANT_ID",
                            "Invalid tenant identifier");
                    return;
                }
                TenantContext.setSchemaName(schemaName);
            } else if (StringUtils.hasText(schemaName)) {
                TenantContext.setSchemaName(schemaName);
            } else if (StringUtils.hasText(tenantId)) {
                log.warn("Ignoring invalid tenant id for schema resolution: {}", tenantId);
            }

            String userId = extractUserId();
            if (StringUtils.hasText(userId)) {
                TenantContext.setUserId(userId);
            }

            filterChain.doFilter(request, response);
        } catch (BusinessException ex) {
            writeBusinessError(response, ex.getStatus().value(), ex.getErrorCode(), ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String tenantFromHeader = TenantAccessValidator.normalize(request.getHeader(TENANT_HEADER));

        boolean isSuperAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

        if (isSuperAdmin && tenantFromHeader != null) {
            log.debug("SUPER_ADMIN override tenant via header: {}", tenantFromHeader);
            return tenantFromHeader;
        }

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tenantFromJwt = TenantAccessValidator.normalize(jwt.getClaimAsString(TENANT_CLAIM));
            return TenantAccessValidator.resolveTenantId(tenantFromHeader, tenantFromJwt, false);
        }

        return tenantFromHeader;
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

    private void writeBusinessError(HttpServletResponse response,
                                    int status,
                                    String errorCode,
                                    String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", StringUtils.hasText(errorCode) ? errorCode : "BUSINESS_ERROR");
        body.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}