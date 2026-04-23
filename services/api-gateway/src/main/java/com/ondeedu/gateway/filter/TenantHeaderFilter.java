package com.ondeedu.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String USER_HEADER = "X-User-ID";
    private static final String BRANCH_HEADER = "X-Branch-ID";
    private static final String TENANT_CLAIM = "tenant_id";
    private static final String BRANCH_CLAIM = "branch_id";
    private static final String BRANCH_IDS_CLAIM = "branch_ids";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(securityContext -> {
                Authentication authentication = securityContext.getAuthentication();
                if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                    String tenantFromHeader = normalizeTenant(exchange.getRequest().getHeaders().getFirst(TENANT_HEADER));
                    String tenantFromJwt = normalizeTenant(jwt.getClaimAsString(TENANT_CLAIM));
                    boolean isSuperAdmin = authentication.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

                    if (!isSuperAdmin
                            && StringUtils.hasText(tenantFromHeader)
                            && StringUtils.hasText(tenantFromJwt)
                            && !tenantFromHeader.equals(tenantFromJwt)) {
                        log.warn("Rejected tenant mismatch in gateway: requestTenant={}, jwtTenant={}, path={}",
                                tenantFromHeader, tenantFromJwt, exchange.getRequest().getPath());
                        return rejectTenantMismatch(exchange);
                    }

                    String effectiveTenantId = StringUtils.hasText(tenantFromHeader) ? tenantFromHeader : tenantFromJwt;
                    String userId = resolveUserId(jwt);
                    String branchId = resolveBranchId(exchange, jwt);

                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                    if (StringUtils.hasText(effectiveTenantId)) {
                        requestBuilder.header(TENANT_HEADER, effectiveTenantId);
                        log.debug("Forwarding tenant header: {}", effectiveTenantId);
                    }
                    if (userId != null) {
                        requestBuilder.header(USER_HEADER, userId);
                    }
                    if (StringUtils.hasText(branchId)) {
                        requestBuilder.header(BRANCH_HEADER, branchId);
                        log.debug("Forwarding branch header: {}", branchId);
                    }

                    ServerWebExchange mutatedExchange = exchange.mutate()
                            .request(requestBuilder.build())
                            .build();
                    return chain.filter(mutatedExchange);
                }
                return chain.filter(exchange);
            })
            .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private String resolveUserId(Jwt jwt) {
        if (StringUtils.hasText(jwt.getSubject())) {
            return jwt.getSubject();
        }

        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (StringUtils.hasText(preferredUsername)) {
            return preferredUsername;
        }

        String email = jwt.getClaimAsString("email");
        return StringUtils.hasText(email) ? email : null;
    }

    private String normalizeTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : null;
    }

    private String resolveBranchId(ServerWebExchange exchange, Jwt jwt) {
        // 1. Try from X-Branch-ID header (frontend sends this when user switches branch)
        String branchFromHeader = normalizeUuid(exchange.getRequest().getHeaders().getFirst(BRANCH_HEADER));
        if (StringUtils.hasText(branchFromHeader)) {
            return branchFromHeader;
        }

        // 2. Try from JWT branch_id claim (single active branch)
        String branchFromJwt = normalizeUuid(jwt.getClaimAsString(BRANCH_CLAIM));
        if (StringUtils.hasText(branchFromJwt)) {
            return branchFromJwt;
        }

        // 3. Try from JWT branch_ids claim (first branch if multiple allowed)
        Object branchIdsObj = jwt.getClaim(BRANCH_IDS_CLAIM);
        if (branchIdsObj instanceof java.util.List<?> branchIds && !branchIds.isEmpty()) {
            return normalizeUuid(String.valueOf(branchIds.get(0)));
        }

        return null;
    }

    private String normalizeUuid(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Mono<Void> rejectTenantMismatch(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap("""
                {"success":false,"message":"Authenticated tenant does not match the requested tenant context"}
                """.trim().getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
