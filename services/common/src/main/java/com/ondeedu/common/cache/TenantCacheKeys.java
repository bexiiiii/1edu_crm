package com.ondeedu.common.cache;

import com.ondeedu.common.tenant.TenantContext;

import java.util.Objects;

public final class TenantCacheKeys {

    private TenantCacheKeys() {
    }

    public static String fixed(String suffix) {
        return tenantPrefix() + "::" + suffix;
    }

    public static String id(Object id) {
        return tenantPrefix() + "::" + Objects.toString(id, "null");
    }

    private static String tenantPrefix() {
        String tenantId = TenantContext.getTenantId();
        return (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
    }
}
