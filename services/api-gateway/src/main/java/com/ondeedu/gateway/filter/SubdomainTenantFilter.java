package com.ondeedu.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubdomainTenantFilter implements GlobalFilter, Ordered {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.domain:1edu.kz}")
    private String baseDomain;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // If X-Tenant-ID is already present, skip subdomain resolution
        if (exchange.getRequest().getHeaders().containsKey(TENANT_HEADER)) {
            return chain.filter(exchange);
        }

        String host = exchange.getRequest().getHeaders().getFirst("Host");
        if (host == null) {
            return chain.filter(exchange);
        }

        // Strip port from host
        String hostWithoutPort = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;

        // Validate host ends with .{baseDomain} and extract single-level subdomain
        String suffix = "." + baseDomain;
        if (!hostWithoutPort.endsWith(suffix)) {
            return chain.filter(exchange);
        }

        String subdomain = hostWithoutPort.substring(0, hostWithoutPort.length() - suffix.length());

        // Only allow single-level subdomains (no dots)
        if (subdomain.isEmpty() || subdomain.contains(".")) {
            return chain.filter(exchange);
        }

        String cacheKey = "subdomain:" + subdomain;

        return reactiveStringRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(tenantId -> {
                    log.debug("Resolved tenant from cache: subdomain={}, tenantId={}", subdomain, tenantId);
                    return chain.filter(withTenantHeader(exchange, tenantId));
                })
                .switchIfEmpty(Mono.defer(() ->
                        webClientBuilder.build()
                                .get()
                                .uri("lb://tenant-service/internal/tenants/subdomain/{s}", subdomain)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .mapNotNull(body -> (String) ((Map<?, ?>) body.get("data")).get("id"))
                                .flatMap(tenantId -> {
                                    String id = Objects.requireNonNull(tenantId);
                                    Duration ttl = Objects.requireNonNull(CACHE_TTL);
                                    return reactiveStringRedisTemplate.opsForValue()
                                            .set(cacheKey, id, ttl)
                                            .thenReturn(id);
                                })
                                .flatMap(tenantId -> {
                                    log.debug("Resolved tenant from service: subdomain={}, tenantId={}", subdomain, tenantId);
                                    return chain.filter(withTenantHeader(exchange, tenantId));
                                })
                                .onErrorResume(ex -> {
                                    log.warn("Failed to resolve tenant for subdomain '{}': {}", subdomain, ex.getMessage());
                                    return chain.filter(exchange);
                                })
                ));
    }

    private ServerWebExchange withTenantHeader(ServerWebExchange exchange, String tenantId) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TENANT_HEADER, tenantId)
                .build();
        return exchange.mutate().request(mutatedRequest).build();
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
