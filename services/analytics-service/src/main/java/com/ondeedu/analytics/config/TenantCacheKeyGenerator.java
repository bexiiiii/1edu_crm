package com.ondeedu.analytics.config;

import com.ondeedu.common.tenant.TenantContext;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * Tenant-aware cache key generator.
 *
 * <p>Формат ключа: {@code {tenantId}::{className}::{methodName}::{arg0}::{arg1}...}
 *
 * <p>Обязателен для multi-tenant сервисов: без tenant-prefix данные одного
 * тенанта могут быть возвращены другому.
 *
 * <p>Использование:
 * <pre>{@code
 * @Cacheable(value = AnalyticsCacheNames.DASHBOARD, keyGenerator = "tenantCacheKeyGenerator")
 * public DashboardResponse getDashboard(...) { ... }
 * }</pre>
 */
@Component("tenantCacheKeyGenerator")
public class TenantCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }

        StringJoiner joiner = new StringJoiner("::");
        joiner.add(tenantId);
        joiner.add(target.getClass().getSimpleName());
        joiner.add(method.getName());
        Arrays.stream(params).forEach(p -> joiner.add(p != null ? p.toString() : "null"));

        return joiner.toString();
    }
}
