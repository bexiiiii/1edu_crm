package com.ondeedu.common.tenant;

import com.ondeedu.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_BRANCH = new ThreadLocal<>();

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setSchemaName(String schemaName) {
        CURRENT_SCHEMA.set(schemaName);
    }

    public static String getSchemaName() {
        String schema = CURRENT_SCHEMA.get();
        if (!StringUtils.hasText(schema)) {
            throw new BusinessException(
                    "TENANT_CONTEXT_REQUIRED",
                    "Tenant context is required for this operation",
                    HttpStatus.BAD_REQUEST
            );
        }
        return schema;
    }

    public static String getSchemaNameOrNull() {
        return CURRENT_SCHEMA.get();
    }

    public static void setUserId(String userId) {
        CURRENT_USER.set(userId);
    }

    public static String getUserId() {
        return CURRENT_USER.get();
    }

    public static void setBranchId(String branchId) {
        CURRENT_BRANCH.set(branchId);
    }

    public static String getBranchId() {
        return CURRENT_BRANCH.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_SCHEMA.remove();
        CURRENT_USER.remove();
        CURRENT_BRANCH.remove();
    }
}
