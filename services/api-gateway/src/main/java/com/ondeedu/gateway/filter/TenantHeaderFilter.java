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
    private static final String TENANT_CLAIM = "tenant_id";

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

                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                    if (StringUtils.hasText(effectiveTenantId)) {
                        requestBuilder.header(TENANT_HEADER, effectiveTenantId);
                        log.debug("Forwarding tenant header: {}", effectiveTenantId);
                    }
                    if (userId != null) {
                        requestBuilder.header(USER_HEADER, userId);
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

    private Mono<Void> rejectTenantMismatch(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap("""
                {"success":false,"message":"Authenticated tenant does not match the requested tenant context"}
                """.trim().getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
