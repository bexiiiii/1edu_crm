package com.ondeedu.common.cache;

import com.ondeedu.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.UUID;

@Slf4j
@Component("tenantCacheKeyGenerator")
public class TenantCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            // CRITICAL: no tenant context — generate a random key so cache is always missed
            // and no cross-tenant data leakage occurs via shared cache entries.
            // Log at ERROR level so this is easily detectable in production.
            log.error("TenantCacheKeyGenerator: tenantId is null for {}.{}() — cache bypassed to prevent cross-tenant data leak",
                    target.getClass().getSimpleName(), method.getName());
            return "no-tenant::" + UUID.randomUUID();
        }

            String branchId = TenantContext.getBranchId();
            String branchScope = (branchId == null || branchId.isBlank())
                ? "no-branch"
                : branchId.trim();

        StringJoiner joiner = new StringJoiner("::");
        joiner.add(tenantId);
            joiner.add(branchScope);
        joiner.add(target.getClass().getSimpleName());
        joiner.add(method.getName());
        Arrays.stream(params).forEach(param -> joiner.add(param != null ? param.toString() : "null"));
        return joiner.toString();
    }
}
