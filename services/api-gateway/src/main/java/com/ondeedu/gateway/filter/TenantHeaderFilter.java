package com.ondeedu.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String USER_HEADER = "X-User-ID";
    private static final String TENANT_CLAIM = "tenant_id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> {
                if (securityContext.getAuthentication() != null &&
                    securityContext.getAuthentication().getPrincipal() instanceof Jwt jwt) {

                    String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
                    String userId = jwt.getSubject();

                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                    if (tenantId != null) {
                        requestBuilder.header(TENANT_HEADER, tenantId);
                        log.debug("Added tenant header: {}", tenantId);
                    }
                    if (userId != null) {
                        requestBuilder.header(USER_HEADER, userId);
                    }

                    return exchange.mutate().request(requestBuilder.build()).build();
                }
                return exchange;
            })
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
