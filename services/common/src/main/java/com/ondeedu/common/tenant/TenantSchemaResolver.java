package com.ondeedu.common.tenant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantSchemaResolver {

    private static final String DEFAULT_SCHEMA = "tenant_default";

    public static String schemaNameForTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }

        String normalizedTenantId = tenantId.trim().replace("-", "");
        if (!normalizedTenantId.matches("^[a-zA-Z0-9_]+$")) {
            return null;
        }

        return "tenant_" + normalizedTenantId;
    }

    public static String defaultSchema() {
        return DEFAULT_SCHEMA;
    }
}
