package com.ondeedu.common.cache;

import com.ondeedu.common.tenant.TenantContext;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.StringJoiner;

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
        Arrays.stream(params).forEach(param -> joiner.add(param != null ? param.toString() : "null"));
        return joiner.toString();
    }
}
