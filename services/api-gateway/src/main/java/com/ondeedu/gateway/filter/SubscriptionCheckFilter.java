package com.ondeedu.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCheckFilter implements GlobalFilter, Ordered {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_PREFIX = "sub-status:";
    private static final String TENANT_HEADER = "X-Tenant-ID";

    /** Paths that bypass subscription check */
    private static final Set<String> BYPASS_PREFIXES = Set.of(
        "/api/v1/register",
        "/api/v1/subscription/plans",
        "/api/v1/subscription/status",
        "/api/v1/auth/",
        "/auth/",
        "/actuator/",
        "/fallback/",
        "/swagger-ui",
        "/v3/api-docs",
        "/webjars/",
        "/internal/"
    );

    private final ReactiveRedisTemplate<String, String> reactiveStringRedisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip bypass paths
        if (shouldBypass(path)) {
            return chain.filter(exchange);
        }

        String tenantId = exchange.getRequest().getHeaders().getFirst(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            return chain.filter(exchange);
        }

        String cacheKey = CACHE_PREFIX + tenantId;

        return reactiveStringRedisTemplate.opsForValue().get(cacheKey)
            .flatMap(cached -> {
                String accessState = cached;
                return isAccessAllowed(accessState)
                    ? chain.filter(exchange)
                    : rejectWithPaymentRequired(exchange, accessState);
            })
            .switchIfEmpty(Mono.defer(() ->
                webClientBuilder.build()
                    .get()
                    .uri("lb://tenant-service/internal/tenants/{id}/subscription-status", tenantId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .flatMap(body -> {
                        String accessState = extractAccessState(body);
                        if (accessState == null) return chain.filter(exchange);

                        return reactiveStringRedisTemplate.opsForValue()
                            .set(cacheKey, accessState, CACHE_TTL)
                            .then(
                                isAccessAllowed(accessState)
                                    ? chain.filter(exchange)
                                    : rejectWithPaymentRequired(exchange, accessState)
                            );
                    })
                    .onErrorResume(ex -> {
                        log.warn("Subscription check failed for tenant {}: {}", tenantId, ex.getMessage());
                        // Fail open — allow request if we can't check status
                        return chain.filter(exchange);
                    })
            ));
    }

    private boolean shouldBypass(String path) {
        return BYPASS_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isAccessAllowed(String accessState) {
        return "TRIAL_ACTIVE".equals(accessState) || "SUBSCRIPTION_ACTIVE".equals(accessState);
    }

    private Mono<Void> rejectWithPaymentRequired(ServerWebExchange exchange, String accessState) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYMENT_REQUIRED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorCode = mapToErrorCode(accessState);
        String body = """
            {"success":false,"errorCode":"%s","message":"%s"}
            """.formatted(errorCode, mapToMessage(accessState)).strip();

        DataBuffer buffer = response.bufferFactory()
            .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private String extractAccessState(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) return null;
            JsonNode stateNode = data.path("accessState");
            return stateNode.isMissingNode() ? null : stateNode.asText();
        } catch (Exception e) {
            log.warn("Failed to parse subscription status response: {}", e.getMessage());
            return null;
        }
    }

    private String mapToErrorCode(String accessState) {
        return switch (accessState == null ? "" : accessState) {
            case "TRIAL_EXPIRED"        -> "TRIAL_EXPIRED";
            case "SUBSCRIPTION_EXPIRED" -> "SUBSCRIPTION_EXPIRED";
            case "SUSPENDED"            -> "SUBSCRIPTION_SUSPENDED";
            case "BANNED"               -> "TENANT_BANNED";
            case "INACTIVE"             -> "SUBSCRIPTION_INACTIVE";
            default                     -> "SUBSCRIPTION_REQUIRED";
        };
    }

    private String mapToMessage(String accessState) {
        return switch (accessState == null ? "" : accessState) {
            case "TRIAL_EXPIRED"        -> "Пробный период истёк. Выберите тариф для продолжения.";
            case "SUBSCRIPTION_EXPIRED" -> "Подписка истекла. Продлите тариф для продолжения.";
            case "SUSPENDED"            -> "Аккаунт приостановлен. Свяжитесь с поддержкой.";
            case "BANNED"               -> "Аккаунт заблокирован.";
            case "INACTIVE"             -> "Аккаунт неактивен.";
            default                     -> "Требуется активная подписка.";
        };
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
