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
        String safeTenant = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;

        String branchId = TenantContext.getBranchId();
        String safeBranch = (branchId == null || branchId.isBlank()) ? "no-branch" : branchId.trim();

        return safeTenant + "::" + safeBranch;
    }
}
